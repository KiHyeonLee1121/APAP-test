"""Tests for the feature caching layer.

Runnable two ways:
    ./venv/bin/python -m tests.test_feature_cache      # standalone, no pytest
    ./venv/bin/pytest tests/test_feature_cache.py      # if pytest installed

The pose extractor is faked, so these tests need no real mp4/MediaPipe.
"""
from __future__ import annotations

import hashlib
import os
import sys
import tempfile
from contextlib import contextmanager
from pathlib import Path

import numpy as np

# Allow running as a plain script from the project root.
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from src import dataset
from src.feature_cache import (
    FeatureCache,
    LocalPathMtimeKeyProvider,
)


@contextmanager
def _patch(obj, name, value):
    original = getattr(obj, name)
    setattr(obj, name, value)
    try:
        yield
    finally:
        setattr(obj, name, original)


def _landmarks_for(path: Path, frames: int = 5) -> np.ndarray:
    """Deterministic fake pose landmarks (frames, 33, 4) derived from the name.

    Mirrors extract_pose_landmarks' output structure: variable frame count,
    33 MediaPipe joints, (x, y, z, visibility) on the last axis.
    """
    seed = abs(hash(path.name)) % (2**32)
    rng = np.random.default_rng(seed)
    return rng.random((frames, 33, 4)).astype(np.float32)


def _feature_from_landmarks(landmarks: np.ndarray) -> np.ndarray:
    """Deterministic fake 26-dim feature computed from landmark content.

    Same landmarks -> same feature, so a value coming from cache or from a fresh
    extraction is comparable. Standing in for make_feature_vector.
    """
    digest = hashlib.sha256(
        np.ascontiguousarray(landmarks, dtype=np.float32).tobytes()
    ).digest()
    seed = int.from_bytes(digest[:8], "big")
    rng = np.random.default_rng(seed)
    return rng.random(26).astype(np.float32)


def _make_video_tree(root: Path) -> None:
    for label_dir in ("normal", "abnormal"):
        (root / label_dir).mkdir(parents=True, exist_ok=True)
    for name in ("a_01.mp4", "b_02.mp4"):
        (root / "normal" / name).write_bytes(b"fake")
    (root / "abnormal" / "c_03.mp4").write_bytes(b"fake")


# --------------------------------------------------------------------------- #
# CacheKeyProvider
# --------------------------------------------------------------------------- #
def test_key_is_stable_and_path_sensitive():
    provider = LocalPathMtimeKeyProvider()
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        file_a = tmp_path / "a.mp4"
        file_b = tmp_path / "b.mp4"
        file_a.write_bytes(b"x")
        file_b.write_bytes(b"y")

        key_a1 = provider.get_key(file_a)
        key_a2 = provider.get_key(file_a)
        key_b = provider.get_key(file_b)

        assert key_a1 == key_a2, "same file must yield a stable key"
        assert key_a1 != key_b, "different paths must yield different keys"
        assert len(key_a1) == 64 and all(c in "0123456789abcdef" for c in key_a1)


def test_key_changes_with_mtime():
    provider = LocalPathMtimeKeyProvider()
    with tempfile.TemporaryDirectory() as tmp:
        file_a = Path(tmp) / "a.mp4"
        file_a.write_bytes(b"x")
        key_before = provider.get_key(file_a)

        # Bump mtime to a clearly different value.
        stat = file_a.stat()
        os.utime(file_a, ns=(stat.st_atime_ns, stat.st_mtime_ns + 1_000_000_000))

        key_after = provider.get_key(file_a)
        assert key_before != key_after, "mtime change must invalidate the key"


# --------------------------------------------------------------------------- #
# FeatureCache
# --------------------------------------------------------------------------- #
def test_cache_roundtrip_and_miss():
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        video = tmp_path / "v.mp4"
        video.write_bytes(b"x")
        cache = FeatureCache(cache_dir=tmp_path / "cache")

        assert cache.load(video) is None, "missing entry must be a miss"

        landmarks = _landmarks_for(video)
        assert cache.store(video, landmarks) is True
        loaded = cache.load(video)

        assert loaded is not None
        assert np.array_equal(loaded, landmarks)
        assert loaded.dtype == np.float32
        assert loaded.shape == landmarks.shape  # variable-length landmarks preserved


def test_structurally_invalid_cache_is_miss():
    """A stale 26-dim feature-vector cache (wrong structure) is treated as a miss."""
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        video = tmp_path / "v.mp4"
        video.write_bytes(b"x")
        cache = FeatureCache(cache_dir=tmp_path / "cache")

        # Write an old-style (26,) feature vector to the exact cache path.
        cache_path = cache.cache_path_for(video)
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        np.save(cache_path, np.zeros(26, dtype=np.float32))

        assert cache.load(video) is None, "wrong-structure entry must be a miss"


# --------------------------------------------------------------------------- #
# build_dataset integration
# --------------------------------------------------------------------------- #
def _build(raw_dir: Path):
    # augment=False: these tests are about landmark caching, not augmentation
    # (that has its own coverage in test_augmentation.py), so keep the sample
    # count at one row per window here.
    return dataset.build_dataset(
        raw_data_dir=raw_dir,
        include_synthetic=False,
        augment=False,
    )


def test_build_dataset_cache_miss_then_hit():
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        raw_dir = tmp_path / "raw"
        _make_video_tree(raw_dir)
        cache_dir = tmp_path / "cache"

        calls = {"extract": 0, "feature": 0}

        def fake_extract(video_path: str):
            calls["extract"] += 1
            return _landmarks_for(Path(video_path))

        def fake_make_feature_vector(landmarks):
            calls["feature"] += 1
            return _feature_from_landmarks(landmarks)

        with _patch(dataset, "extract_pose_landmarks", fake_extract), _patch(
            dataset, "make_feature_vector", fake_make_feature_vector
        ), _patch(dataset, "FeatureCache", lambda: FeatureCache(cache_dir=cache_dir)):
            # First run: all misses -> extractor runs for every video.
            first = _build(raw_dir)
            assert calls["extract"] == 3, "first run should extract every video"
            assert calls["feature"] == 3, "feature vector built for every video"
            assert len(list(cache_dir.glob("*.npy"))) == 3, "cache files created"

            # Second run: all hits -> extractor never runs, but features rebuild.
            calls["extract"] = 0
            calls["feature"] = 0
            second = _build(raw_dir)
            assert calls["extract"] == 0, "second run must skip extraction (cache hit)"
            assert calls["feature"] == 3, (
                "make_feature_vector must run every time, even on a cache hit"
            )

        # Features identical between the fresh run and the cached run.
        assert np.array_equal(first.features, second.features)
        assert np.array_equal(first.labels, second.labels)


def test_cached_result_matches_uncached():
    """A cache-served build must equal a from-scratch (empty cache) build."""
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        raw_dir = tmp_path / "raw"
        _make_video_tree(raw_dir)

        def fake_extract(video_path: str):
            return _landmarks_for(Path(video_path))

        def run_with_cache(cache_dir: Path):
            with _patch(dataset, "extract_pose_landmarks", fake_extract), _patch(
                dataset, "make_feature_vector", _feature_from_landmarks
            ), _patch(
                dataset, "FeatureCache", lambda: FeatureCache(cache_dir=cache_dir)
            ):
                return _build(raw_dir)

        uncached = run_with_cache(tmp_path / "cache_a")  # empty cache -> computes
        warm_dir = tmp_path / "cache_b"
        run_with_cache(warm_dir)                          # populate
        cached = run_with_cache(warm_dir)                 # served from cache

        assert np.array_equal(uncached.features, cached.features)


# --------------------------------------------------------------------------- #
# Corrupted cache
# --------------------------------------------------------------------------- #
def test_corrupted_cache_load_returns_none():
    """A corrupted .npy must not raise from load(); it returns None (a miss)."""
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        video = tmp_path / "v.mp4"
        video.write_bytes(b"x")
        cache = FeatureCache(cache_dir=tmp_path / "cache")

        cache_path = cache.cache_path_for(video)
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        cache_path.write_bytes(b"this is not a valid npy file")

        assert cache.load(video) is None, "corrupt entry must be a silent miss"


def test_build_dataset_falls_back_on_corrupted_cache():
    """Corrupt cache files must trigger re-extraction, not lost samples."""
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        raw_dir = tmp_path / "raw"
        _make_video_tree(raw_dir)
        cache_dir = tmp_path / "cache"
        cache_dir.mkdir(parents=True, exist_ok=True)

        # Pre-corrupt the cache entry for every video.
        keyed_cache = FeatureCache(cache_dir=cache_dir)
        videos = [
            raw_dir / "normal" / "a_01.mp4",
            raw_dir / "normal" / "b_02.mp4",
            raw_dir / "abnormal" / "c_03.mp4",
        ]
        for video in videos:
            keyed_cache.cache_path_for(video).write_bytes(b"corrupt")

        calls = {"extract": 0}

        def fake_extract(video_path: str):
            calls["extract"] += 1
            return _landmarks_for(Path(video_path))

        with _patch(dataset, "extract_pose_landmarks", fake_extract), _patch(
            dataset, "make_feature_vector", _feature_from_landmarks
        ), _patch(dataset, "FeatureCache", lambda: FeatureCache(cache_dir=cache_dir)):
            result = _build(raw_dir)

        assert calls["extract"] == 3, "corrupt cache must fall back to extraction"
        assert result.features.shape == (3, 26), "all samples produced, none skipped"
        assert not result.skipped, "no video should be skipped due to cache corruption"
        # Corrupt files were overwritten with valid landmarks during the run.
        assert keyed_cache.load(videos[0]) is not None


# --------------------------------------------------------------------------- #
# Standalone runner
# --------------------------------------------------------------------------- #
def _run_all() -> int:
    tests = [
        obj
        for name, obj in sorted(globals().items())
        if name.startswith("test_") and callable(obj)
    ]
    failures = 0
    for test in tests:
        try:
            test()
        except Exception as exc:  # noqa: BLE001 - report every failure
            failures += 1
            print(f"[FAIL] {test.__name__}: {type(exc).__name__}: {exc}")
        else:
            print(f"[PASS] {test.__name__}")

    print(f"\n{len(tests) - failures}/{len(tests)} passed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(_run_all())
