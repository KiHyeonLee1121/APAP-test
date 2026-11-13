from __future__ import annotations

from pathlib import Path
from typing import Any

import numpy as np

try:
    import cv2
except ImportError as exc:  # pragma: no cover - depends on local environment
    cv2 = None
    _CV2_IMPORT_ERROR = exc
else:
    _CV2_IMPORT_ERROR = None

try:
    import mediapipe as mp
except ImportError as exc:  # pragma: no cover - depends on local environment
    mp = None
    _MEDIAPIPE_IMPORT_ERROR = exc
else:
    _MEDIAPIPE_IMPORT_ERROR = None

try:
    import torch
except ImportError as exc:  # pragma: no cover - depends on local environment
    torch = None
    _TORCH_IMPORT_ERROR = exc
else:
    _TORCH_IMPORT_ERROR = None

from ..features import make_feature_vector
from ..realtime.stream_buffer import FrameBuffer
from .model import load_autoencoder
from .train import AUTOENCODER_PATH


def _require_realtime_dependencies() -> None:
    missing = []
    if cv2 is None:
        missing.append("opencv-python")
    if mp is None:
        missing.append("mediapipe")
    if torch is None:
        missing.append("torch")

    if missing:
        packages = ", ".join(missing)
        raise RuntimeError(
            f"Missing required package(s): {packages}. "
            "Install dependencies with `pip install -r requirements.txt`."
        ) from (_CV2_IMPORT_ERROR or _MEDIAPIPE_IMPORT_ERROR or _TORCH_IMPORT_ERROR)


def _landmarks_to_array(pose_landmarks: Any) -> np.ndarray:
    return np.asarray(
        [
            [landmark.x, landmark.y, landmark.z, landmark.visibility]
            for landmark in pose_landmarks.landmark
        ],
        dtype=np.float32,
    )


class RealtimeAnomalyProcessor:
    """Stateful sliding-window realtime anomaly detector.

    Mirrors ``RealtimePoseProcessor`` but uses an Autoencoder instead of a
    RandomForest classifier. A window of pose landmarks is converted into a
    feature vector, scaled with the training scaler, reconstructed by the
    Autoencoder, and flagged as ``abnormal`` when the reconstruction error
    exceeds the stored threshold.

    Create one processor per camera stream when camera-specific buffers should
    be isolated. Backend services can import this class and call process_frame
    with preprocessed np.ndarray frames.
    """

    def __init__(
        self,
        model_path: str | Path = AUTOENCODER_PATH,
        window_size: int = 30,
        frame_skip: int = 1,
        prediction_interval: int = 10,
        camera_id: str = "default",
        input_color_format: str = "BGR",
        min_detection_confidence: float = 0.5,
        min_tracking_confidence: float = 0.5,
        model_complexity: int = 1,
    ) -> None:
        _require_realtime_dependencies()

        if window_size <= 0:
            raise ValueError("window_size must be greater than 0.")
        if frame_skip <= 0:
            raise ValueError("frame_skip must be greater than 0.")
        if prediction_interval <= 0:
            raise ValueError("prediction_interval must be greater than 0.")

        normalized_color_format = input_color_format.strip().upper()
        if normalized_color_format not in {"BGR", "RGB"}:
            raise ValueError("input_color_format must be either 'BGR' or 'RGB'.")

        self.model, self.scaler, self.threshold = load_autoencoder(model_path)
        self.window_size = window_size
        self.frame_skip = frame_skip
        self.prediction_interval = prediction_interval
        self.camera_id = camera_id
        self.input_color_format = normalized_color_format
        self.pose_buffer: FrameBuffer[np.ndarray] = FrameBuffer(max_size=window_size)
        self.frames_seen = 0
        self.frames_processed = 0
        self.predictions_made = 0

        self._pose = mp.solutions.pose.Pose(
            static_image_mode=False,
            model_complexity=model_complexity,
            enable_segmentation=False,
            min_detection_confidence=min_detection_confidence,
            min_tracking_confidence=min_tracking_confidence,
        )

    def close(self) -> None:
        self._pose.close()

    def reset(self) -> None:
        self.pose_buffer.clear()
        self.frames_seen = 0
        self.frames_processed = 0
        self.predictions_made = 0

    def __enter__(self) -> "RealtimeAnomalyProcessor":
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        self.close()

    def _extract_landmarks_from_frame(self, frame: np.ndarray) -> np.ndarray | None:
        if frame is None:
            raise ValueError("frame must not be None.")
        if not isinstance(frame, np.ndarray):
            raise TypeError("frame must be a numpy ndarray.")
        if frame.ndim != 3 or frame.shape[2] < 3:
            raise ValueError(
                "frame must have shape (height, width, channels) with at least 3 channels."
            )

        frame_3ch = frame[:, :, :3]
        if self.input_color_format == "BGR":
            rgb_frame = cv2.cvtColor(frame_3ch, cv2.COLOR_BGR2RGB)
        else:
            rgb_frame = frame_3ch

        rgb_frame.flags.writeable = False
        result = self._pose.process(rgb_frame)
        if result.pose_landmarks is None:
            return None

        return _landmarks_to_array(result.pose_landmarks)

    def _predict_from_buffer(self) -> dict:
        landmarks = np.asarray(self.pose_buffer.get_window(), dtype=np.float32)
        feature_vector = make_feature_vector(landmarks)

        # Apply the same scaling used during training, then reconstruct.
        feature_scaled = self.scaler.transform(
            feature_vector.reshape(1, -1)
        ).astype(np.float32)
        x = torch.tensor(feature_scaled)
        with torch.no_grad():
            reconstructed = self.model(x)
            error = float(torch.mean((reconstructed - x) ** 2).item())

        is_anomaly = error > self.threshold
        self.predictions_made += 1
        return {
            "prediction": "abnormal" if is_anomaly else "normal",
            "is_anomaly": is_anomaly,
            "reconstruction_error": error,
            "threshold": self.threshold,
            "window_size": self.window_size,
            "buffer_size": len(self.pose_buffer),
            "camera_id": self.camera_id,
            "frames_seen": self.frames_seen,
            "frames_processed": self.frames_processed,
            "predictions_made": self.predictions_made,
            "status": "predicted",
        }

    def _status_update(self, status: str) -> dict:
        return {
            "prediction": None,
            "is_anomaly": False,
            "reconstruction_error": None,
            "threshold": self.threshold,
            "window_size": self.window_size,
            "buffer_size": len(self.pose_buffer),
            "camera_id": self.camera_id,
            "frames_seen": self.frames_seen,
            "frames_processed": self.frames_processed,
            "status": status,
        }

    def process_frame(self, frame: np.ndarray) -> dict | None:
        """Process one frame and return a prediction update when available."""
        self.frames_seen += 1
        if (self.frames_seen - 1) % self.frame_skip != 0:
            return None

        self.frames_processed += 1
        landmarks = self._extract_landmarks_from_frame(frame)
        if landmarks is None:
            return self._status_update("no_pose")

        self.pose_buffer.add_frame(landmarks)
        if not self.pose_buffer.is_ready():
            return self._status_update("warming_up")

        if self.frames_processed % self.prediction_interval != 0:
            return None

        return self._predict_from_buffer()
