from __future__ import annotations

from dataclasses import dataclass
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


@dataclass
class RTSPReaderConfig:
    reconnect_attempts: int = 3
    frame_skip: int = 1
    resize_width: int | None = None
    resize_height: int | None = None


class RTSPReader:
    """Simple RTSP frame reader.

    This MVP uses OpenCV VideoCapture. Reconnect backoff, async buffering, and
    production-grade stream health checks can be added behind this interface.
    """

    def __init__(
        self,
        rtsp_url: str,
        reconnect_attempts: int = 3,
        frame_skip: int = 1,
        resize_width: int | None = None,
        resize_height: int | None = None,
    ) -> None:
        if not rtsp_url.lower().startswith("rtsp://"):
            raise ValueError("rtsp_url must start with 'rtsp://'.")
        if reconnect_attempts < 0:
            raise ValueError("reconnect_attempts must be greater than or equal to 0.")
        if frame_skip <= 0:
            raise ValueError("frame_skip must be greater than 0.")

        self.rtsp_url = rtsp_url
        self.config = RTSPReaderConfig(
            reconnect_attempts=reconnect_attempts,
            frame_skip=frame_skip,
            resize_width=resize_width,
            resize_height=resize_height,
        )
        self.capture: Any | None = None
        self.frames_seen = 0

    def open(self) -> bool:
        _require_cv2()
        self.capture = cv2.VideoCapture(self.rtsp_url)
        return self.is_opened()

    def is_opened(self) -> bool:
        return bool(self.capture is not None and self.capture.isOpened())

    def reconnect(self) -> bool:
        self.release()
        for _ in range(self.config.reconnect_attempts):
            if self.open():
                return True
        return False

    def read(self) -> tuple[bool, Any | None]:
        if not self.is_opened() and not self.reconnect():
            return False, None

        while True:
            success, frame = self.capture.read()
            if not success:
                if self.reconnect():
                    continue
                return False, None

            self.frames_seen += 1
            if (self.frames_seen - 1) % self.config.frame_skip != 0:
                continue

            if self.config.resize_width and self.config.resize_height:
                frame = cv2.resize(
                    frame,
                    (self.config.resize_width, self.config.resize_height),
                )
            return True, frame

    def release(self) -> None:
        if self.capture is not None:
            self.capture.release()
            self.capture = None

    def __enter__(self) -> "RTSPReader":
        self.open()
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        self.release()
