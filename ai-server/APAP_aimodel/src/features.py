from __future__ import annotations

import math

import numpy as np


LANDMARK_INDEX = {
    "left_shoulder": 11,
    "right_shoulder": 12,
    "left_elbow": 13,
    "right_elbow": 14,
    "left_wrist": 15,
    "right_wrist": 16,
    "left_hip": 23,
    "right_hip": 24,
    "left_knee": 25,
    "right_knee": 26,
    "left_ankle": 27,
    "right_ankle": 28,
}

DISTANCE_PAIRS = (
    ("left_wrist", "right_wrist"),
    ("left_wrist", "left_shoulder"),
    ("right_wrist", "right_shoulder"),
    ("left_shoulder", "right_shoulder"),
    ("left_hip", "right_hip"),
)

ANGLE_TRIPLETS = (
    ("left_shoulder", "left_elbow", "left_wrist"),
    ("right_shoulder", "right_elbow", "right_wrist"),
    ("left_hip", "left_knee", "left_ankle"),
    ("right_hip", "right_knee", "right_ankle"),
)

APAP_OBJECT_CLASSES = (
    "person",
    "bag",
    "atm",
    "railing",
    "product",
    "phone",
    "door",
    "other",
)


def _validate_landmarks(landmarks: np.ndarray) -> np.ndarray:
    array = np.asarray(landmarks, dtype=np.float32)
    if array.ndim != 3 or array.shape[-1] != 4:
        raise ValueError(
            "Landmarks must have shape (num_frames, num_landmarks, 4). "
            f"Got {array.shape}."
        )
    if array.shape[0] == 0:
        raise ValueError("Landmarks contain no detected frames.")

    required_index = max(LANDMARK_INDEX.values())
    if array.shape[1] <= required_index:
        raise ValueError(
            "Landmarks do not contain the required MediaPipe Pose indices. "
            f"Need index {required_index}, got {array.shape[1]} landmarks."
        )

    return np.nan_to_num(array, nan=0.0, posinf=0.0, neginf=0.0)


def _summary(values: np.ndarray) -> np.ndarray:
    clean_values = np.asarray(values, dtype=np.float32)
    if clean_values.size == 0:
        return np.asarray([0.0, 0.0], dtype=np.float32)

    return np.asarray(
        [float(np.mean(clean_values)), float(np.std(clean_values))],
        dtype=np.float32,
    )


def _coordinate_summary(landmarks: np.ndarray) -> np.ndarray:
    flattened = landmarks.reshape(-1, landmarks.shape[-1])
    means = np.mean(flattened, axis=0)
    stds = np.std(flattened, axis=0)
    return np.concatenate([means, stds]).astype(np.float32)


def _point(landmarks: np.ndarray, name: str) -> np.ndarray:
    return landmarks[:, LANDMARK_INDEX[name], :3]


def _distance_between(landmarks: np.ndarray, first: str, second: str) -> np.ndarray:
    first_points = _point(landmarks, first)
    second_points = _point(landmarks, second)
    return np.linalg.norm(first_points - second_points, axis=1)


def _distance_features(landmarks: np.ndarray) -> np.ndarray:
    features = [
        _summary(_distance_between(landmarks, first, second))
        for first, second in DISTANCE_PAIRS
    ]
    return np.concatenate(features).astype(np.float32)


def _angle_between(a: np.ndarray, b: np.ndarray, c: np.ndarray) -> float:
    ba = a - b
    bc = c - b
    denominator = np.linalg.norm(ba) * np.linalg.norm(bc)
    if denominator == 0:
        return 0.0

    cosine = float(np.dot(ba, bc) / denominator)
    cosine = max(-1.0, min(1.0, cosine))
    return math.degrees(math.acos(cosine))


def _joint_angle_series(
    landmarks: np.ndarray,
    first: str,
    center: str,
    third: str,
) -> np.ndarray:
    first_points = _point(landmarks, first)
    center_points = _point(landmarks, center)
    third_points = _point(landmarks, third)

    return np.asarray(
        [
            _angle_between(first_point, center_point, third_point)
            for first_point, center_point, third_point in zip(
                first_points,
                center_points,
                third_points,
            )
        ],
        dtype=np.float32,
    )


def _angle_features(landmarks: np.ndarray) -> np.ndarray:
    features = [
        _summary(_joint_angle_series(landmarks, first, center, third))
        for first, center, third in ANGLE_TRIPLETS
    ]
    return np.concatenate(features).astype(np.float32)


def make_feature_vector(landmarks: np.ndarray) -> np.ndarray:
    """Create a fixed-size feature vector from pose landmarks."""
    validated_landmarks = _validate_landmarks(landmarks)
    feature_vector = np.concatenate(
        [
            _coordinate_summary(validated_landmarks),
            _distance_features(validated_landmarks),
            _angle_features(validated_landmarks),
        ]
    )
    return feature_vector.astype(np.float32)


def make_object_features(
    detections: list[dict],
    class_names: tuple[str, ...] = APAP_OBJECT_CLASSES,
) -> np.ndarray:
    """Create optional fixed-size object features from detector outputs.

    This function is intentionally not used by train.py/infer.py by default,
    because the current model was trained on pose-only features.
    """
    class_counts = {class_name: 0.0 for class_name in class_names}
    max_confidence = 0.0

    for detection in detections or []:
        class_name = str(detection.get("class_name", "other")).lower()
        if class_name not in class_counts:
            class_name = "other"

        class_counts[class_name] += 1.0
        confidence = float(detection.get("confidence", 0.0) or 0.0)
        max_confidence = max(max_confidence, confidence)

    person_count = class_counts.get("person", 0.0)
    total_count = float(len(detections or []))
    count_features = [class_counts[class_name] for class_name in class_names]

    return np.asarray(
        [person_count, total_count, *count_features, max_confidence],
        dtype=np.float32,
    )


def combine_features(
    pose_features: np.ndarray,
    object_features: np.ndarray | None = None,
) -> np.ndarray:
    """Combine pose and optional object features.

    Passing object_features=None preserves the original pose-only feature
    shape used by the MVP train/infer pipeline.
    """
    pose_array = np.asarray(pose_features, dtype=np.float32).reshape(-1)
    if object_features is None:
        return pose_array

    object_array = np.asarray(object_features, dtype=np.float32).reshape(-1)
    return np.concatenate([pose_array, object_array]).astype(np.float32)
