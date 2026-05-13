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

from ..detection.tracker import ObjectTracker
from ..detection.yolo_detector import DEFAULT_YOLO_MODEL, YOLODetector
from ..features import combine_features, make_feature_vector, make_object_features
from ..model import load_model
from ..utils import MODEL_PATH
from .stream_buffer import FrameBuffer


LABEL_NAMES = {0: "normal", 1: "abnormal"}


def _require_realtime_dependencies() -> None:
    missing = []
    if cv2 is None:
        missing.append("opencv-python")
    if mp is None:
        missing.append("mediapipe")

    if missing:
        packages = ", ".join(missing)
        raise RuntimeError(
            f"Missing required package(s): {packages}. "
            "Install dependencies with `pip install -r requirements.txt`."
        ) from (_CV2_IMPORT_ERROR or _MEDIAPIPE_IMPORT_ERROR)


def _landmarks_to_array(pose_landmarks: Any) -> np.ndarray:
    return np.asarray(
        [
            [landmark.x, landmark.y, landmark.z, landmark.visibility]
            for landmark in pose_landmarks.landmark
        ],
        dtype=np.float32,
    )


class RealtimePoseProcessor:
    """Stateful sliding-window realtime pose classifier.

    Create one processor per camera stream when camera-specific buffers should
    be isolated. Backend services can import this class and call process_frame
    with preprocessed np.ndarray frames.
    """

    def __init__(
        self,
        model_path: str | Path = MODEL_PATH,
        window_size: int = 30,
        frame_skip: int = 1,
        prediction_interval: int = 10,
        camera_id: str = "default",
        input_color_format: str = "BGR",
        use_object_detection: bool = False,
        use_tracking: bool = False,
        use_object_features_for_prediction: bool = False,
        yolo_model_path: str | Path = DEFAULT_YOLO_MODEL,
        yolo_confidence_threshold: float = 0.25,
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

        self.model = load_model(model_path)
        self.window_size = window_size
        self.frame_skip = frame_skip
        self.prediction_interval = prediction_interval
        self.camera_id = camera_id
        self.input_color_format = normalized_color_format
        self.use_object_detection = use_object_detection
        self.use_tracking = use_tracking
        self.use_object_features_for_prediction = use_object_features_for_prediction
        self.pose_buffer: FrameBuffer[np.ndarray] = FrameBuffer(max_size=window_size)
        self.frames_seen = 0
        self.frames_processed = 0
        self.predictions_made = 0
        self.detector = (
            YOLODetector(
                model_path=yolo_model_path,
                confidence_threshold=yolo_confidence_threshold,
            )
            if use_object_detection
            else None
        )
        self.tracker = ObjectTracker() if use_tracking else None

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

    def __enter__(self) -> "RealtimePoseProcessor":
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

    def _process_objects(self, frame: np.ndarray) -> dict:
        if not self.detector:
            return {
                "detections": [],
                "tracked_objects": [],
                "object_features": (
                    make_object_features([])
                    if self.use_object_features_for_prediction
                    else None
                ),
                "object_error": None,
            }

        try:
            detections = self.detector.detect(frame)
            tracked_objects = (
                self.tracker.update(detections, frame=frame)
                if self.tracker
                else detections
            )
            object_features = make_object_features(tracked_objects)
        except Exception as exc:
            return {
                "detections": [],
                "tracked_objects": [],
                "object_features": make_object_features([]),
                "object_error": str(exc),
            }

        return {
            "detections": detections,
            "tracked_objects": tracked_objects,
            "object_features": object_features,
            "object_error": None,
        }

    def _predict_from_buffer(self, object_features: np.ndarray | None = None) -> dict:
        landmarks = np.asarray(self.pose_buffer.get_window(), dtype=np.float32)
        pose_features = make_feature_vector(landmarks)
        feature_vector = combine_features(
            pose_features=pose_features,
            object_features=(
                object_features
                if self.use_object_features_for_prediction
                else None
            ),
        ).reshape(1, -1)

        if hasattr(self.model, "predict_proba"):
            probabilities = self.model.predict_proba(feature_vector)[0]
            best_index = int(np.argmax(probabilities))
            raw_label = int(self.model.classes_[best_index])
            confidence = float(probabilities[best_index])
        else:
            raw_label = int(self.model.predict(feature_vector)[0])
            confidence = 1.0

        self.predictions_made += 1
        return {
            "prediction": LABEL_NAMES.get(raw_label, str(raw_label)),
            "confidence": confidence,
            "raw_label": raw_label,
            "window_size": self.window_size,
            "buffer_size": len(self.pose_buffer),
            "camera_id": self.camera_id,
            "frames_seen": self.frames_seen,
            "frames_processed": self.frames_processed,
            "predictions_made": self.predictions_made,
            "status": "predicted",
        }

    def process_frame(self, frame: np.ndarray) -> dict | None:
        """Process one frame and return a prediction update when available."""
        self.frames_seen += 1
        if (self.frames_seen - 1) % self.frame_skip != 0:
            return None

        self.frames_processed += 1
        landmarks = self._extract_landmarks_from_frame(frame)
        object_result = self._process_objects(frame)
        if landmarks is None:
            return {
                "prediction": None,
                "confidence": 0.0,
                "window_size": self.window_size,
                "buffer_size": len(self.pose_buffer),
                "camera_id": self.camera_id,
                "frames_seen": self.frames_seen,
                "frames_processed": self.frames_processed,
                "detections": object_result["detections"],
                "tracked_objects": object_result["tracked_objects"],
                "object_error": object_result["object_error"],
                "status": "no_pose",
            }

        self.pose_buffer.add_frame(landmarks)
        if not self.pose_buffer.is_ready():
            return {
                "prediction": None,
                "confidence": 0.0,
                "window_size": self.window_size,
                "buffer_size": len(self.pose_buffer),
                "camera_id": self.camera_id,
                "frames_seen": self.frames_seen,
                "frames_processed": self.frames_processed,
                "detections": object_result["detections"],
                "tracked_objects": object_result["tracked_objects"],
                "object_error": object_result["object_error"],
                "status": "warming_up",
            }

        if self.frames_processed % self.prediction_interval != 0:
            return None

        prediction = self._predict_from_buffer(object_result["object_features"])
        prediction["detections"] = object_result["detections"]
        prediction["tracked_objects"] = object_result["tracked_objects"]
        prediction["object_error"] = object_result["object_error"]
        return prediction
