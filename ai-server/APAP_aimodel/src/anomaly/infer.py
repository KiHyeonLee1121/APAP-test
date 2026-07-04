from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path

try:
    import torch
except ImportError as exc:
    torch = None
    _TORCH_IMPORT_ERROR = exc
else:
    _TORCH_IMPORT_ERROR = None

from ..extract_pose import extract_pose_landmarks
from ..features import make_feature_vector
from ..utils import log_error
from .model import load_autoencoder
from .train import AUTOENCODER_PATH


@dataclass
class AnomalyResult:
    label: str
    is_anomaly: bool
    reconstruction_error: float
    threshold: float


def predict_anomaly(
    video_path: str | Path,
    model_path: str | Path = AUTOENCODER_PATH,
) -> AnomalyResult:
    if torch is None:
        raise RuntimeError(
            "Missing required package: torch. Install with `pip install torch`."
        ) from _TORCH_IMPORT_ERROR

    model, scaler, threshold = load_autoencoder(model_path)

    landmarks = extract_pose_landmarks(str(video_path))
    feature_vector = make_feature_vector(landmarks)

    # Apply the same scaling used during training
    feature_scaled = scaler.transform(feature_vector.reshape(1, -1)).astype("float32")
    x = torch.tensor(feature_scaled)
    with torch.no_grad():
        reconstructed = model(x)
        error = float(torch.mean((reconstructed - x) ** 2).item())

    is_anomaly = error > threshold
    return AnomalyResult(
        label="abnormal" if is_anomaly else "normal",
        is_anomaly=is_anomaly,
        reconstruction_error=error,
        threshold=threshold,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Autoencoder-based anomaly detection inference.")
    parser.add_argument("--video", required=True, help="Path to an mp4 video file.")
    parser.add_argument(
        "--model",
        default=str(AUTOENCODER_PATH),
        help="Path to a trained autoencoder checkpoint (.pt).",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        result = predict_anomaly(args.video, args.model)
    except FileNotFoundError as exc:
        log_error(str(exc))
        return 1
    except Exception as exc:
        log_error(str(exc))
        return 1

    print(f"Prediction:           {result.label}")
    print(f"Anomaly:              {result.is_anomaly}")
    print(f"Reconstruction error: {result.reconstruction_error:.6f}")
    print(f"Threshold:            {result.threshold:.6f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
