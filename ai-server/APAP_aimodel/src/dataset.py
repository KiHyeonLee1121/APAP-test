from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import numpy as np

try:
    from tqdm import tqdm
except ImportError:  # pragma: no cover - depends on local environment
    tqdm = None

from .augmentation import GAUSSIAN_NOISE_STD, add_gaussian_noise, mirror_flip
from .extract_pose import extract_pose_landmarks, read_video_fps
from .feature_cache import FeatureCache
from .features import make_feature_vector
from .windowing import split_into_windows
from .utils import (
    ABNORMAL_DATA_DIR,
    NORMAL_DATA_DIR,
    RAW_DATA_DIR,
    SYNTHETIC_ABNORMAL_VIDEO_DIR,
    SYNTHETIC_NORMAL_VIDEO_DIR,
    SYNTHETIC_VIDEO_DIR,
    format_path,
    list_video_files,
    log_warning,
)


@dataclass
class DatasetBundle:
    features: np.ndarray
    labels: np.ndarray
    paths: list[Path]
    skipped: list[tuple[Path, str]]


def _iter_with_progress(items: list[tuple[Path, int]]):
    if tqdm is None:
        return items
    return tqdm(items, desc="Processing videos", unit="video")


def _collect_labeled_videos(normal_dir: Path, abnormal_dir: Path) -> list[tuple[Path, int]]:
    labeled_videos = [(path, 0) for path in list_video_files(normal_dir)]
    labeled_videos.extend((path, 1) for path in list_video_files(abnormal_dir))
    return labeled_videos


def _find_raw_videos(raw_data_dir: str | Path) -> list[tuple[Path, int]]:
    raw_dir = Path(raw_data_dir)
    return _collect_labeled_videos(
        normal_dir=raw_dir / "normal",
        abnormal_dir=raw_dir / "abnormal",
    )


def _find_synthetic_videos(
    synthetic_video_dir: str | Path,
) -> list[tuple[Path, int]]:
    synthetic_dir = Path(synthetic_video_dir)
    if not synthetic_dir.exists():
        log_warning(
            "Synthetic video directory was not found; continuing without "
            f"synthetic data: {format_path(synthetic_dir)}"
        )
        return []

    synthetic_videos = _collect_labeled_videos(
        normal_dir=synthetic_dir / "normal",
        abnormal_dir=synthetic_dir / "abnormal",
    )
    if not synthetic_videos:
        log_warning(
            "No synthetic mp4 videos found; continuing with available real data. "
            f"Expected locations: {format_path(SYNTHETIC_NORMAL_VIDEO_DIR)}, "
            f"{format_path(SYNTHETIC_ABNORMAL_VIDEO_DIR)}."
        )

    return synthetic_videos


def find_labeled_videos(
    raw_data_dir: str | Path = RAW_DATA_DIR,
    synthetic_video_dir: str | Path = SYNTHETIC_VIDEO_DIR,
    include_synthetic: bool = True,
) -> list[tuple[Path, int]]:
    raw_videos = _find_raw_videos(raw_data_dir)
    synthetic_videos = (
        _find_synthetic_videos(synthetic_video_dir)
        if include_synthetic
        else []
    )

    labeled_videos = raw_videos + synthetic_videos
    if not labeled_videos:
        raise FileNotFoundError(
            "No mp4 training videos found. Put normal videos in "
            f"{format_path(NORMAL_DATA_DIR)} and abnormal videos in "
            f"{format_path(ABNORMAL_DATA_DIR)}. Synthetic videos can also be "
            f"placed in {format_path(SYNTHETIC_VIDEO_DIR)}."
        )

    return labeled_videos


def build_dataset(
    raw_data_dir: str | Path = RAW_DATA_DIR,
    synthetic_video_dir: str | Path = SYNTHETIC_VIDEO_DIR,
    include_synthetic: bool = True,
    augment: bool = True,
    augmentation_seed: int = 42,
) -> DatasetBundle:
    labeled_videos = find_labeled_videos(
        raw_data_dir=raw_data_dir,
        synthetic_video_dir=synthetic_video_dir,
        include_synthetic=include_synthetic,
    )

    features: list[np.ndarray] = []
    labels: list[int] = []
    used_paths: list[Path] = []
    skipped: list[tuple[Path, str]] = []

    cache = FeatureCache()
    # One Generator per build_dataset() call: same seed -> same augmentation
    # noise draws, in the same (deterministic) video/window processing order.
    rng = np.random.default_rng(augmentation_seed) if augment else None

    for video_path, label in _iter_with_progress(labeled_videos):
        try:
            landmarks = cache.load(video_path)
            if landmarks is None:
                landmarks = extract_pose_landmarks(str(video_path))
                cache.store(video_path, landmarks)
            # Windowing is applied after the cache load, so the cache format is
            # unchanged. fps is read from the video header (cheap) rather than
            # stored in the cache; unknown fps falls back to one window.
            fps = read_video_fps(str(video_path))
            windows = split_into_windows(landmarks, fps)

            # Augmentation runs in-memory on the loaded (cached) landmarks, per
            # window; only the original landmarks are ever cached/stored.
            window_variants: list[np.ndarray] = []
            for window in windows:
                window_variants.append(window)
                if augment:
                    window_variants.append(mirror_flip(window))
                    window_variants.append(
                        add_gaussian_noise(window, GAUSSIAN_NOISE_STD, rng)
                    )

            window_features = [make_feature_vector(w) for w in window_variants]
        except Exception as exc:
            skipped.append((video_path, str(exc)))
            log_warning(f"Skipping {format_path(video_path)}: {exc}")
            continue

        # One video expands into several rows (one per window, x3 when
        # augmented). label and path are inherited from the source video.
        for feature_vector in window_features:
            features.append(feature_vector)
            labels.append(label)
            used_paths.append(video_path)

    if not features:
        raise RuntimeError(
            "No valid training samples were created. Check that the mp4 files "
            "contain a visible person and can be opened by OpenCV."
        )

    return DatasetBundle(
        features=np.vstack(features).astype(np.float32),
        labels=np.asarray(labels, dtype=np.int64),
        paths=used_paths,
        skipped=skipped,
    )
