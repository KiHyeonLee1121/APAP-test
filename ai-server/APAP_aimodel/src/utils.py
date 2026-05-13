from __future__ import annotations

from pathlib import Path
from typing import Iterable


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = PROJECT_ROOT / "data"
RAW_DATA_DIR = DATA_DIR / "raw"
NORMAL_DATA_DIR = RAW_DATA_DIR / "normal"
ABNORMAL_DATA_DIR = RAW_DATA_DIR / "abnormal"
PROCESSED_DATA_DIR = DATA_DIR / "processed"
SYNTHETIC_DATA_DIR = DATA_DIR / "synthetic"
SYNTHETIC_VIDEO_DIR = SYNTHETIC_DATA_DIR / "videos"
SYNTHETIC_NORMAL_VIDEO_DIR = SYNTHETIC_VIDEO_DIR / "normal"
SYNTHETIC_ABNORMAL_VIDEO_DIR = SYNTHETIC_VIDEO_DIR / "abnormal"
SYNTHETIC_METADATA_DIR = SYNTHETIC_DATA_DIR / "metadata"
CHECKPOINTS_DIR = PROJECT_ROOT / "checkpoints"
OUTPUTS_DIR = PROJECT_ROOT / "outputs"
MODEL_PATH = CHECKPOINTS_DIR / "model_v1.pkl"

VIDEO_EXTENSIONS = (".mp4",)


def ensure_dir(path: str | Path) -> Path:
    directory = Path(path)
    directory.mkdir(parents=True, exist_ok=True)
    return directory


def ensure_project_dirs() -> None:
    for directory in (
        NORMAL_DATA_DIR,
        ABNORMAL_DATA_DIR,
        PROCESSED_DATA_DIR,
        SYNTHETIC_NORMAL_VIDEO_DIR,
        SYNTHETIC_ABNORMAL_VIDEO_DIR,
        SYNTHETIC_METADATA_DIR,
        CHECKPOINTS_DIR,
        OUTPUTS_DIR,
    ):
        ensure_dir(directory)


def list_video_files(
    directory: str | Path,
    extensions: Iterable[str] = VIDEO_EXTENSIONS,
) -> list[Path]:
    video_dir = Path(directory)
    if not video_dir.exists():
        return []

    normalized_extensions = tuple(ext.lower() for ext in extensions)
    return sorted(
        path
        for path in video_dir.iterdir()
        if path.is_file() and path.suffix.lower() in normalized_extensions
    )


def format_path(path: str | Path) -> str:
    try:
        return str(Path(path).resolve().relative_to(PROJECT_ROOT))
    except ValueError:
        return str(Path(path))


def log_info(message: str) -> None:
    print(f"[INFO] {message}")


def log_warning(message: str) -> None:
    print(f"[WARNING] {message}")


def log_error(message: str) -> None:
    print(f"[ERROR] {message}")
