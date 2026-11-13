from __future__ import annotations

import hashlib
from pathlib import Path
from typing import Protocol, runtime_checkable

import numpy as np

from .utils import PROCESSED_DATA_DIR, ensure_dir, format_path, log_warning


# Structural expectations for cached pose landmarks.
# extract_pose_landmarks returns shape (num_frames, num_landmarks, 4) where the
# frame count varies per video, MediaPipe Pose yields 33 landmarks, and the last
# axis is (x, y, z, visibility). Validation is structural (not a fixed shape) so
# it tolerates the variable frame count while rejecting corrupt/incompatible
# entries. The minimum landmark count matches what make_feature_vector needs
# (it indexes up to MediaPipe index 28 = right_ankle).
_COORD_DIM = 4
_MIN_LANDMARKS = 29


def _is_valid_landmarks(array: np.ndarray) -> bool:
    return (
        array.ndim == 3
        and array.shape[0] >= 1
        and array.shape[1] >= _MIN_LANDMARKS
        and array.shape[2] == _COORD_DIM
    )


@runtime_checkable
class CacheKeyProvider(Protocol):
    """Produces a filesystem-safe cache key identifying a video source.

    Implementations decide what identity/version information goes into the key.
    Keys identify the video source only, so they are independent of what is
    cached (landmarks) and of any label. Swapping the provider (e.g. a future
    ``S3KeyETagProvider`` based on object key + ETag) changes nothing for the
    cache store/lookup logic or for ``build_dataset``.
    """

    def get_key(self, video_path: Path) -> str: ...


class LocalPathMtimeKeyProvider:
    """Cache key from a local file's resolved path + modification time.

    The absolute path and the file's mtime (nanoseconds) are combined and
    hashed with sha256, yielding a fixed-length, filesystem-safe key. If the
    file is re-encoded or edited its mtime changes, so the key changes and the
    stale cache entry is naturally bypassed.
    """

    def get_key(self, video_path: Path) -> str:
        path = Path(video_path)
        resolved = str(path.resolve())
        mtime_ns = path.stat().st_mtime_ns
        raw = f"{resolved}:{mtime_ns}".encode("utf-8")
        return hashlib.sha256(raw).hexdigest()


class FeatureCache:
    """Stores/loads pose landmarks as ``.npy`` files under a cache directory.

    Landmarks are cached (rather than the final feature vector) because
    extract_pose_landmarks is the heavy step; make_feature_vector is light pure
    numpy and always runs on top of the cached landmarks. Caching is fully
    transparent: any failure to read or write a cache entry degrades to a normal
    miss/no-op, so a cache problem never changes which samples ``build_dataset``
    produces.
    """

    def __init__(
        self,
        cache_dir: str | Path = PROCESSED_DATA_DIR,
        key_provider: CacheKeyProvider | None = None,
    ) -> None:
        self.cache_dir = Path(cache_dir)
        self.key_provider = key_provider or LocalPathMtimeKeyProvider()

    def cache_path_for(self, video_path: Path) -> Path:
        key = self.key_provider.get_key(video_path)
        return self.cache_dir / f"{key}.npy"

    def load(self, video_path: Path) -> np.ndarray | None:
        """Return cached pose landmarks, or None on any miss/error/bad structure."""
        try:
            cache_path = self.cache_path_for(video_path)
        except OSError:
            # Key generation needs the source file (e.g. stat for mtime); if it
            # is gone, treat as a miss and let the normal path raise its error.
            return None

        if not cache_path.exists():
            return None

        try:
            landmarks = np.load(cache_path).astype(np.float32)
        except Exception:
            # Corrupt or unreadable cache entry: recompute instead of failing.
            return None

        if not _is_valid_landmarks(landmarks):
            # Structurally incompatible entry (e.g. old feature-vector cache):
            # treat as a miss so the original extraction path runs.
            return None

        return landmarks

    def store(self, video_path: Path, landmarks: np.ndarray) -> bool:
        """Persist pose landmarks. Returns False (without raising) on failure."""
        try:
            ensure_dir(self.cache_dir)
            cache_path = self.cache_path_for(video_path)
            np.save(cache_path, np.asarray(landmarks, dtype=np.float32))
            return True
        except Exception as exc:
            log_warning(
                f"Landmark cache write failed for {format_path(video_path)}: {exc}"
            )
            return False
