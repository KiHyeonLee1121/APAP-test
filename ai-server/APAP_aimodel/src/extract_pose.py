from __future__ import annotations

from pathlib import Path

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


def _require_pose_dependencies() -> None:
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


def _landmarks_to_array(pose_landmarks) -> np.ndarray:
    return np.asarray(
        [
            [landmark.x, landmark.y, landmark.z, landmark.visibility]
            for landmark in pose_landmarks.landmark
        ],
        dtype=np.float32,
    )


def extract_pose_landmarks(
    video_path: str,
    min_detection_confidence: float = 0.5,
    min_tracking_confidence: float = 0.5,
    model_complexity: int = 1,
) -> np.ndarray:
    """Extract MediaPipe Pose landmarks from an mp4 video.

    Returns:
        np.ndarray with shape (num_detected_frames, num_landmarks, 4).
        The last dimension stores x, y, z, visibility.
    """
    _require_pose_dependencies()

    path = Path(video_path)
    if not path.exists():
        raise FileNotFoundError(f"Video file not found: {path}")
    if not path.is_file():
        raise ValueError(f"Video path is not a file: {path}")

    capture = cv2.VideoCapture(str(path))
    if not capture.isOpened():
        raise RuntimeError(f"Could not open video file: {path}")

    detected_landmarks: list[np.ndarray] = []
    total_frames = 0

    try:
        with mp.solutions.pose.Pose(
            static_image_mode=False,
            model_complexity=model_complexity,
            enable_segmentation=False,
            min_detection_confidence=min_detection_confidence,
            min_tracking_confidence=min_tracking_confidence,
        ) as pose:
            while True:
                success, frame = capture.read()
                if not success:
                    break

                total_frames += 1
                rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                rgb_frame.flags.writeable = False

                result = pose.process(rgb_frame)
                if result.pose_landmarks is None:
                    continue

                detected_landmarks.append(_landmarks_to_array(result.pose_landmarks))
    finally:
        capture.release()

    if not detected_landmarks:
        raise ValueError(
            f"No pose landmarks detected in video: {path}. "
            f"Processed frames: {total_frames}."
        )

    landmarks = np.asarray(detected_landmarks, dtype=np.float32)
    if landmarks.ndim != 3 or landmarks.shape[-1] != 4:
        raise RuntimeError(
            "Unexpected landmark shape. "
            f"Expected (frames, landmarks, 4), got {landmarks.shape}."
        )

    return landmarks
