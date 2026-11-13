from __future__ import annotations

import os
import tempfile
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator

try:
    import boto3
except ImportError as exc:  # pragma: no cover - depends on local environment
    boto3 = None
    _BOTO3_IMPORT_ERROR = exc
else:
    _BOTO3_IMPORT_ERROR = None


# Same environment variable names the backend's storage config uses
# (backend/src/main/resources/application.yml: apap.storage.mode/s3.bucket,
# AWS_REGION) so both services read one shared deployment config, no
# AI-specific env vars to keep in sync.
STORAGE_MODE_ENV = "APP_STORAGE_MODE"
S3_BUCKET_ENV = "S3_BUCKET"
AWS_REGION_ENV = "AWS_REGION"


def _storage_mode() -> str:
    return os.environ.get(STORAGE_MODE_ENV, "local").strip().lower()


@contextmanager
def resolve_video_path(video_path: str) -> Iterator[Path]:
    """Yield a local filesystem path usable with cv2.VideoCapture.

    In local mode (default), video_path is already a local path — the backend's
    LocalStorageService stores sourceUrl as a local path, so it's yielded
    unchanged.

    In s3 mode, video_path is an S3 object key (the backend's S3StorageService
    stores sourceUrl as the key, e.g. "videos/{uuid}-{filename}") — the object
    is downloaded to a temporary file, yielded, and removed on exit.
    Credentials come from boto3's default chain (EC2 instance profile role in
    deployment; this path cannot be exercised from a local dev machine, which
    has no S3 access keys for this account).
    """
    if _storage_mode() != "s3":
        yield Path(video_path)
        return

    if boto3 is None:
        raise RuntimeError(
            "Missing required package: boto3. Install with `pip install boto3` "
            f"(required when {STORAGE_MODE_ENV}=s3)."
        ) from _BOTO3_IMPORT_ERROR

    bucket = os.environ.get(S3_BUCKET_ENV)
    if not bucket:
        raise RuntimeError(
            f"{S3_BUCKET_ENV} environment variable is required when "
            f"{STORAGE_MODE_ENV}=s3."
        )
    region = os.environ.get(AWS_REGION_ENV)

    suffix = Path(video_path).suffix or ".mp4"
    fd, tmp_name = tempfile.mkstemp(suffix=suffix)
    os.close(fd)
    tmp_path = Path(tmp_name)

    try:
        client = boto3.client("s3", region_name=region) if region else boto3.client("s3")
        client.download_file(bucket, video_path, str(tmp_path))
        yield tmp_path
    finally:
        tmp_path.unlink(missing_ok=True)
