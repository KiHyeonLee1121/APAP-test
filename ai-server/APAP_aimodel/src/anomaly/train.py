from __future__ import annotations

import sys

import numpy as np

try:
    import torch
    import torch.nn as nn
    from torch.utils.data import DataLoader, TensorDataset
except ImportError as exc:
    torch = None
    _TORCH_IMPORT_ERROR = exc
else:
    _TORCH_IMPORT_ERROR = None

try:
    from sklearn.preprocessing import StandardScaler
except ImportError as exc:
    StandardScaler = None
    _SKLEARN_IMPORT_ERROR = exc
else:
    _SKLEARN_IMPORT_ERROR = None

from ..dataset import find_labeled_videos
from ..extract_pose import extract_pose_landmarks
from ..features import make_feature_vector
from ..utils import (
    CHECKPOINTS_DIR,
    ensure_project_dirs,
    format_path,
    log_error,
    log_info,
    log_warning,
)
from .model import create_autoencoder, save_autoencoder

AUTOENCODER_PATH = CHECKPOINTS_DIR / "autoencoder_v1.pt"

# Threshold multiplier: threshold = mean_error + SIGMA_MULTIPLIER * std_error
SIGMA_MULTIPLIER = 3.0


def _require_torch() -> None:
    if torch is None:
        raise RuntimeError(
            "Missing required package: torch. "
            "Install with `pip install torch`."
        ) from _TORCH_IMPORT_ERROR


def _require_sklearn() -> None:
    if StandardScaler is None:
        raise RuntimeError(
            "Missing required package: scikit-learn. "
            "Install with `pip install scikit-learn`."
        ) from _SKLEARN_IMPORT_ERROR


def _build_normal_features(
    raw_data_dir=None,
    synthetic_video_dir=None,
    include_synthetic: bool = True,
) -> np.ndarray:
    kwargs = {}
    if raw_data_dir is not None:
        kwargs["raw_data_dir"] = raw_data_dir
    if synthetic_video_dir is not None:
        kwargs["synthetic_video_dir"] = synthetic_video_dir
    kwargs["include_synthetic"] = include_synthetic

    labeled_videos = find_labeled_videos(**kwargs)
    normal_videos = [(path, label) for path, label in labeled_videos if label == 0]

    if not normal_videos:
        raise ValueError(
            "No normal videos found. Autoencoder trains on normal data only."
        )

    features: list[np.ndarray] = []
    for video_path, _ in normal_videos:
        try:
            landmarks = extract_pose_landmarks(str(video_path))
            features.append(make_feature_vector(landmarks))
        except Exception as exc:
            log_warning(f"Skipping {format_path(video_path)}: {exc}")

    if not features:
        raise RuntimeError("No valid normal samples could be extracted.")

    log_info(f"Normal training samples: {len(features)}")
    return np.vstack(features).astype(np.float32)


def train_autoencoder(
    epochs: int = 100,
    lr: float = 1e-3,
    batch_size: int = 32,
    model_path=AUTOENCODER_PATH,
) -> object:
    _require_torch()
    _require_sklearn()
    ensure_project_dirs()

    features = _build_normal_features()
    input_dim = features.shape[1]
    log_info(f"Feature dim: {input_dim}")

    # Normalize features so angle (0~180°) and coordinate (0~1) values are on the same scale
    scaler = StandardScaler()
    features_scaled = scaler.fit_transform(features).astype(np.float32)

    x = torch.tensor(features_scaled)
    dataset = TensorDataset(x)
    loader = DataLoader(dataset, batch_size=min(batch_size, len(features)), shuffle=True)

    model = create_autoencoder(input_dim)
    optimizer = torch.optim.Adam(model.parameters(), lr=lr)
    criterion = nn.MSELoss()

    model.train()
    for epoch in range(1, epochs + 1):
        epoch_loss = 0.0
        for (batch,) in loader:
            optimizer.zero_grad()
            reconstructed = model(batch)
            loss = criterion(reconstructed, batch)
            loss.backward()
            optimizer.step()
            epoch_loss += loss.item() * len(batch)

        if epoch % 10 == 0 or epoch == 1:
            log_info(f"Epoch {epoch}/{epochs}  loss={epoch_loss / len(features):.6f}")

    # Compute per-sample reconstruction errors on training set to set threshold
    model.eval()
    with torch.no_grad():
        reconstructed_all = model(x)
        errors = torch.mean((reconstructed_all - x) ** 2, dim=1).numpy()

    threshold = float(np.mean(errors) + SIGMA_MULTIPLIER * np.std(errors))
    log_info(f"Reconstruction error — mean: {np.mean(errors):.6f}, std: {np.std(errors):.6f}")
    log_info(f"Anomaly threshold (mean + {SIGMA_MULTIPLIER}σ): {threshold:.6f}")

    saved = save_autoencoder(model, scaler, threshold, model_path)
    log_info(f"Saved autoencoder: {format_path(saved)}")
    return model


def main() -> int:
    try:
        train_autoencoder()
    except Exception as exc:
        log_error(str(exc))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
