from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import numpy as np

try:
    from tqdm import tqdm
except ImportError:  # pragma: no cover - depends on local environment
    tqdm = None

from .extract_pose import extract_pose_landmarks
from .features import make_feature_vector
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

    for video_path, label in _iter_with_progress(labeled_videos):
        try:
            landmarks = extract_pose_landmarks(str(video_path))
            feature_vector = make_feature_vector(landmarks)
        except Exception as exc:
            skipped.append((video_path, str(exc)))
            log_warning(f"Skipping {format_path(video_path)}: {exc}")
            continue

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
