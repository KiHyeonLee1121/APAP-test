"""Tests for src/video_storage.py's local/s3 video path resolution.

No real AWS access is available on this dev machine (no local access keys for
this account — S3 mode is only exercisable from EC2 with an instance profile
role), so the s3-mode tests mock boto3's S3 client instead of hitting AWS.

Runnable standalone (no pytest) or via pytest.
"""
from __future__ import annotations

import os
import sys
import tempfile
from contextlib import contextmanager
from pathlib import Path
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from src import video_storage


@contextmanager
def _env(**overrides):
    original = {k: os.environ.get(k) for k in overrides}
    os.environ.update(overrides)
    try:
        yield
    finally:
        for k, v in original.items():
            if v is None:
                os.environ.pop(k, None)
            else:
                os.environ[k] = v


def test_local_mode_yields_path_unchanged():
    with _env(APP_STORAGE_MODE="local"):
        with video_storage.resolve_video_path("data/raw/normal/walking_01.mp4") as p:
            assert p == Path("data/raw/normal/walking_01.mp4")


def test_default_mode_is_local_when_env_unset():
    with _env(APP_STORAGE_MODE=""):
        os.environ.pop("APP_STORAGE_MODE", None)
        with video_storage.resolve_video_path("some/local/path.mp4") as p:
            assert p == Path("some/local/path.mp4")


def test_s3_mode_downloads_via_boto3_and_cleans_up():
    downloaded = {}

    def fake_download_file(bucket, key, dest):
        downloaded["bucket"] = bucket
        downloaded["key"] = key
        downloaded["dest"] = dest
        Path(dest).write_bytes(b"fake video bytes")

    fake_client = MagicMock()
    fake_client.download_file.side_effect = fake_download_file

    with _env(APP_STORAGE_MODE="s3", S3_BUCKET="my-bucket", AWS_REGION="us-east-1"):
        with patch.object(video_storage.boto3, "client", return_value=fake_client) as mock_client:
            with video_storage.resolve_video_path("videos/abc-video.mp4") as local_path:
                assert local_path.exists()
                assert local_path.read_bytes() == b"fake video bytes"
                assert local_path.suffix == ".mp4"
                path_during_download = local_path

            mock_client.assert_called_once_with("s3", region_name="us-east-1")

        assert downloaded["bucket"] == "my-bucket"
        assert downloaded["key"] == "videos/abc-video.mp4"

    # Temp file must be cleaned up after the context manager exits.
    assert not path_during_download.exists()


def test_s3_mode_requires_bucket_env():
    with _env(APP_STORAGE_MODE="s3"):
        os.environ.pop("S3_BUCKET", None)
        try:
            with video_storage.resolve_video_path("videos/abc.mp4"):
                pass
            raise AssertionError("expected RuntimeError for missing S3_BUCKET")
        except RuntimeError as exc:
            assert "S3_BUCKET" in str(exc)


def test_s3_mode_cleans_up_temp_file_even_on_download_failure():
    # download_file fails before the generator yields, so the `with ... as p`
    # body never runs — spy on mkstemp to recover the temp path it created.
    fake_client = MagicMock()
    fake_client.download_file.side_effect = RuntimeError("simulated download failure")

    real_mkstemp = tempfile.mkstemp
    created_paths = []

    def spy_mkstemp(*args, **kwargs):
        result = real_mkstemp(*args, **kwargs)
        created_paths.append(Path(result[1]))
        return result

    with _env(APP_STORAGE_MODE="s3", S3_BUCKET="my-bucket"):
        with patch.object(video_storage.boto3, "client", return_value=fake_client):
            with patch.object(video_storage.tempfile, "mkstemp", side_effect=spy_mkstemp):
                try:
                    with video_storage.resolve_video_path("videos/abc.mp4"):
                        pass
                    raise AssertionError("expected the simulated failure to propagate")
                except RuntimeError as exc:
                    assert "simulated download failure" in str(exc)

    assert len(created_paths) == 1
    assert not created_paths[0].exists()


def _run_all() -> int:
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    failures = 0
    for test in tests:
        try:
            test()
        except Exception as exc:  # noqa: BLE001
            failures += 1
            print(f"[FAIL] {test.__name__}: {type(exc).__name__}: {exc}")
        else:
            print(f"[PASS] {test.__name__}")
    print(f"\n{len(tests) - failures}/{len(tests)} passed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(_run_all())
