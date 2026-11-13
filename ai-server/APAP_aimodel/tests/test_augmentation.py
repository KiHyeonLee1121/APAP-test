"""Tests for src/augmentation.py (mirror flip + gaussian noise) and their
integration into build_dataset()'s 3x sample expansion.

Runnable standalone (no pytest) or via pytest. The pose extractor is faked for
the build_dataset integration test, so no real mp4/MediaPipe is needed there;
mirror_flip/add_gaussian_noise themselves need a real mediapipe import (for the
PoseLandmark enum), which is available in this project's venv.
"""
from __future__ import annotations

import sys
import tempfile
from contextlib import contextmanager
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from src import dataset
from src.augmentation import (
    GAUSSIAN_NOISE_STD,
    _build_left_right_swap_map,
    add_gaussian_noise,
    mirror_flip,
)
from src.feature_cache import FeatureCache

LEFT_SHOULDER, RIGHT_SHOULDER = 11, 12
NOSE = 0


@contextmanager
def _patch(obj, name, value):
    original = getattr(obj, name)
    setattr(obj, name, value)
    try:
        yield
    finally:
        setattr(obj, name, original)


def _make_window(frames: int = 4, num_landmarks: int = 33) -> np.ndarray:
    rng = np.random.default_rng(0)
    window = rng.random((frames, num_landmarks, 4)).astype(np.float32)
    window[:, :, 3] = rng.random((frames, num_landmarks))  # visibility in [0,1)
    return window


# --------------------------------------------------------------------------- #
# _build_left_right_swap_map
# --------------------------------------------------------------------------- #
def test_swap_map_covers_all_33_landmarks_bijectively():
    swap_map = _build_left_right_swap_map()
    assert set(swap_map.keys()) == set(range(33))
    # Every mapping must be its own inverse (a <-> b, or a -> a).
    for src, dst in swap_map.items():
        assert swap_map[dst] == src

    # Spot-check a few known LEFT_/RIGHT_ pairs used by features.py.
    assert swap_map[LEFT_SHOULDER] == RIGHT_SHOULDER
    assert swap_map[RIGHT_SHOULDER] == LEFT_SHOULDER
    assert swap_map[NOSE] == NOSE  # central landmark maps to itself


# --------------------------------------------------------------------------- #
# mirror_flip
# --------------------------------------------------------------------------- #
def test_mirror_flip_is_idempotent():
    window = _make_window()
    once = mirror_flip(window)
    twice = mirror_flip(once)
    assert np.allclose(twice, window, atol=1e-6)


def test_mirror_flip_x_coordinate_correctness():
    # Known, simple landmarks: only x differs between left/right shoulder.
    window = np.zeros((1, 33, 4), dtype=np.float32)
    window[0, LEFT_SHOULDER] = [0.3, 0.5, 0.1, 0.9]
    window[0, RIGHT_SHOULDER] = [0.9, 0.6, 0.2, 0.8]
    window[0, NOSE] = [0.4, 0.1, 0.0, 1.0]

    flipped = mirror_flip(window)

    # left slot now holds the flipped-x version of the original right shoulder
    assert np.isclose(flipped[0, LEFT_SHOULDER, 0], 1.0 - 0.9)
    assert np.isclose(flipped[0, RIGHT_SHOULDER, 0], 1.0 - 0.3)
    # y, z, visibility travel with their (now-swapped) landmark, unchanged
    assert np.allclose(flipped[0, LEFT_SHOULDER, 1:], [0.6, 0.2, 0.8])
    assert np.allclose(flipped[0, RIGHT_SHOULDER, 1:], [0.5, 0.1, 0.9])
    # central landmark: only x flips, stays in place
    assert np.isclose(flipped[0, NOSE, 0], 1.0 - 0.4)
    assert np.allclose(flipped[0, NOSE, 1:], [0.1, 0.0, 1.0])


# --------------------------------------------------------------------------- #
# add_gaussian_noise
# --------------------------------------------------------------------------- #
def test_gaussian_noise_shape_and_visibility_unchanged():
    window = _make_window()
    rng = np.random.default_rng(1)
    noisy = add_gaussian_noise(window, std=GAUSSIAN_NOISE_STD, rng=rng)

    assert noisy.shape == window.shape
    assert noisy.dtype == np.float32
    assert np.array_equal(noisy[:, :, 3], window[:, :, 3]), "visibility must be untouched"
    # x, y clipped into [0, 1]; z left unclipped (no assertion needed beyond finiteness).
    assert noisy[:, :, 0].min() >= 0.0 and noisy[:, :, 0].max() <= 1.0
    assert noisy[:, :, 1].min() >= 0.0 and noisy[:, :, 1].max() <= 1.0
    assert np.isfinite(noisy).all()
    # With std > 0, output should actually differ from the input somewhere.
    assert not np.array_equal(noisy[:, :, :3], window[:, :, :3])


# --------------------------------------------------------------------------- #
# build_dataset(augment=...) 3x sample count
# --------------------------------------------------------------------------- #
def _make_video_tree(root: Path) -> None:
    for label_dir in ("normal", "abnormal"):
        (root / label_dir).mkdir(parents=True, exist_ok=True)
    (root / "normal" / "a_01.mp4").write_bytes(b"fake")
    (root / "abnormal" / "b_02.mp4").write_bytes(b"fake")


def test_build_dataset_augment_triples_sample_count():
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        raw_dir = tmp_path / "raw"
        _make_video_tree(raw_dir)
        cache_dir = tmp_path / "cache"

        def fake_extract(video_path: str):
            # fps is unreadable for this fake file -> single-window fallback,
            # so each video contributes exactly one window pre-augmentation.
            return np.random.default_rng(0).random((5, 33, 4)).astype(np.float32)

        def fake_make_feature_vector(_landmarks):
            return np.zeros(26, dtype=np.float32)

        with _patch(dataset, "extract_pose_landmarks", fake_extract), _patch(
            dataset, "make_feature_vector", fake_make_feature_vector
        ), _patch(dataset, "FeatureCache", lambda: FeatureCache(cache_dir=cache_dir)):
            plain = dataset.build_dataset(
                raw_data_dir=raw_dir, include_synthetic=False, augment=False
            )
            augmented = dataset.build_dataset(
                raw_data_dir=raw_dir, include_synthetic=False, augment=True
            )

        assert augmented.features.shape[0] == 3 * plain.features.shape[0]
        assert augmented.labels.shape[0] == 3 * plain.labels.shape[0]
        # Labels are inherited unchanged: same class balance ratio, just x3.
        assert np.array_equal(
            np.sort(np.repeat(plain.labels, 3)), np.sort(augmented.labels)
        )


def test_build_dataset_augment_is_reproducible_with_same_seed():
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        raw_dir = tmp_path / "raw"
        _make_video_tree(raw_dir)
        cache_dir = tmp_path / "cache"

        def fake_extract(video_path: str):
            return np.random.default_rng(0).random((5, 33, 4)).astype(np.float32)

        def fake_make_feature_vector(landmarks):
            # Deterministic function of content, so a changed noise draw
            # between runs would show up as a changed feature value.
            return np.full(26, landmarks.sum(), dtype=np.float32)

        with _patch(dataset, "extract_pose_landmarks", fake_extract), _patch(
            dataset, "make_feature_vector", fake_make_feature_vector
        ), _patch(dataset, "FeatureCache", lambda: FeatureCache(cache_dir=cache_dir)):
            first = dataset.build_dataset(
                raw_data_dir=raw_dir, include_synthetic=False,
                augment=True, augmentation_seed=7,
            )
            second = dataset.build_dataset(
                raw_data_dir=raw_dir, include_synthetic=False,
                augment=True, augmentation_seed=7,
            )

        assert np.array_equal(first.features, second.features)


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
        except Exception as exc:  # noqa: BLE001
            failures += 1
            print(f"[FAIL] {test.__name__}: {type(exc).__name__}: {exc}")
        else:
            print(f"[PASS] {test.__name__}")
    print(f"\n{len(tests) - failures}/{len(tests)} passed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(_run_all())
