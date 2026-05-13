from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from .extract_pose import extract_pose_landmarks
from .features import make_feature_vector
from .model import load_model
from .utils import MODEL_PATH, log_error


LABEL_NAMES = {0: "normal", 1: "abnormal"}


@dataclass
class PredictionResult:
    label: str
    confidence: float
    raw_label: int


def predict_video(video_path: str | Path, model_path: str | Path = MODEL_PATH) -> PredictionResult:
    model = load_model(model_path)
    landmarks = extract_pose_landmarks(str(video_path))
    feature_vector = make_feature_vector(landmarks).reshape(1, -1)

    if hasattr(model, "predict_proba"):
        probabilities = model.predict_proba(feature_vector)[0]
        best_index = int(np.argmax(probabilities))
        raw_label = int(model.classes_[best_index])
        confidence = float(probabilities[best_index])
    else:
        raw_label = int(model.predict(feature_vector)[0])
        confidence = 1.0

    return PredictionResult(
        label=LABEL_NAMES.get(raw_label, str(raw_label)),
        confidence=confidence,
        raw_label=raw_label,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run APAP MVP video inference.")
    parser.add_argument(
        "--video",
        required=True,
        help="Path to an mp4 video file.",
    )
    parser.add_argument(
        "--model",
        default=str(MODEL_PATH),
        help="Path to a trained model checkpoint.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    try:
        result = predict_video(args.video, args.model)
    except FileNotFoundError as exc:
        log_error(str(exc))
        return 1
    except Exception as exc:
        log_error(str(exc))
        return 1

    print(f"Prediction: {result.label}")
    print(f"Confidence: {result.confidence:.2f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
