from __future__ import annotations

from pathlib import Path
from typing import Any

try:
    import cv2
except ImportError as exc:  # pragma: no cover - depends on local environment
    cv2 = None
    _CV2_IMPORT_ERROR = exc
else:
    _CV2_IMPORT_ERROR = None


def _require_cv2() -> None:
    if cv2 is None:
        raise RuntimeError(
            "Missing required package: opencv-python. "
            "Install dependencies with `pip install -r requirements.txt`."
        ) from _CV2_IMPORT_ERROR


def parse_video_source(source: str | int) -> str | int:
    if isinstance(source, int):
        return source

    source_text = str(source).strip()
    if source_text.isdigit():
        return int(source_text)
    return source_text


class VideoSource:
    """Unified OpenCV VideoCapture wrapper for webcam, mp4, and RTSP sources."""

    def __init__(self, source: str | int) -> None:
        self.source = source
        self.parsed_source = parse_video_source(source)
        self.capture: Any | None = None

    def open(self) -> bool:
        _require_cv2()

        if isinstance(
            self.parsed_source,
            str,
        ) and not self.parsed_source.lower().startswith("rtsp://"):
            path = Path(self.parsed_source)
            if not path.exists():
                raise FileNotFoundError(f"Video source not found: {path}")

        self.capture = cv2.VideoCapture(self.parsed_source)
        return self.is_opened()

    def is_opened(self) -> bool:
        return bool(self.capture is not None and self.capture.isOpened())

    def read(self) -> tuple[bool, Any | None]:
        if not self.is_opened():
            return False, None

        try:
            success, frame = self.capture.read()
        except Exception:
            return False, None

        if not success:
            return False, None
        return True, frame

    def release(self) -> None:
        if self.capture is not None:
            self.capture.release()
            self.capture = None

    def __enter__(self) -> "VideoSource":
        self.open()
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        self.release()
