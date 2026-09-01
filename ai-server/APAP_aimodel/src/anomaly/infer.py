from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np

try:
    import torch
except ImportError as exc:
    torch = None
    _TORCH_IMPORT_ERROR = exc
else:
    _TORCH_IMPORT_ERROR = None

from ..extract_pose import extract_pose_landmarks, read_video_fps
from ..features import make_feature_vector
from ..utils import log_error
from ..windowing import split_into_windows, window_time_bounds
from .model import load_autoencoder
from .train import AUTOENCODER_PATH


@dataclass
class AnomalyResult:
    label: str
    is_anomaly: bool
    reconstruction_error: float  # peak (max) reconstruction error across windows
    threshold: float
    num_windows: int
    peak_window_index: int
    peak_window_start_sec: float
    peak_window_end_sec: float


def compute_window_errors(
    landmarks: np.ndarray,
    fps: float,
    model,
    scaler,
) -> np.ndarray:
    """Per-window reconstruction errors (MSE) for one clip's landmarks.

    Shared single source of truth for the error definition: predict_anomaly and
    offline diagnostics both call this so a window's error means the same thing
    everywhere. Returns a 1-D array aligned with split_into_windows(landmarks, fps).
    """
    windows = split_into_windows(landmarks, fps)
    features = np.vstack([make_feature_vector(window) for window in windows])
    features_scaled = scaler.transform(features).astype("float32")
    x = torch.tensor(features_scaled)
    with torch.no_grad():
        reconstructed = model(x)
        errors = torch.mean((reconstructed - x) ** 2, dim=1).numpy()
    return errors


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
    fps = read_video_fps(str(video_path))
    errors = compute_window_errors(landmarks, fps, model, scaler)

    # A clip is anomalous if ANY window exceeds the threshold, so the clip-level
    # score is the peak window error (a brief anomaly is no longer time-averaged
    # away by the calm frames around it).
    peak_index = int(np.argmax(errors))
    peak_error = float(errors[peak_index])
    start_sec, end_sec = window_time_bounds(peak_index, fps)

    is_anomaly = peak_error > threshold
    return AnomalyResult(
        label="abnormal" if is_anomaly else "normal",
        is_anomaly=is_anomaly,
        reconstruction_error=peak_error,
        threshold=threshold,
        num_windows=len(errors),
        peak_window_index=peak_index,
        peak_window_start_sec=start_sec,
        peak_window_end_sec=end_sec,
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
    print(f"Peak error:           {result.reconstruction_error:.6f}  (max over {result.num_windows} windows)")
    print(f"Threshold:            {result.threshold:.6f}")
    print(
        f"Peak window:          #{result.peak_window_index} "
        f"(~{result.peak_window_start_sec:.1f}s–{result.peak_window_end_sec:.1f}s)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
