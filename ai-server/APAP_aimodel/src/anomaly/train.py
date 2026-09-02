from __future__ import annotations

import random
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

from ..dataset import build_dataset
from ..utils import (
    CHECKPOINTS_DIR,
    ensure_project_dirs,
    format_path,
    log_error,
    log_info,
)
from .model import create_autoencoder, save_autoencoder

AUTOENCODER_PATH = CHECKPOINTS_DIR / "autoencoder_v1.pt"

# Threshold multiplier: threshold = mean_error + SIGMA_MULTIPLIER * std_error
SIGMA_MULTIPLIER = 3.0

# Single source of truth for reproducibility: fixes model init + DataLoader
# shuffling (via set_seed) AND the augmentation noise draws in build_dataset
# (passed through as augmentation_seed below), so re-running with the same
# SEED reproduces both the training data and the training process exactly.
SEED = 42

# Percentiles to report alongside mean+3σ for comparison only (see
# compute_percentile_thresholds) — does not change which threshold is used.
COMPARISON_PERCENTILES = (95, 99)


def set_seed(seed: int = SEED) -> None:
    """Fix every source of randomness this training path touches.

    python's `random` module isn't currently used anywhere in this codebase's
    training path (grep-verified), so seeding it here is defensive/future-
    proofing rather than something with an observable effect today.
    """
    random.seed(seed)
    np.random.seed(seed)
    if torch is not None:
        torch.manual_seed(seed)
        if torch.cuda.is_available():
            torch.cuda.manual_seed_all(seed)


def compute_percentile_thresholds(
    errors: np.ndarray,
    percentiles: tuple[int, ...] = COMPARISON_PERCENTILES,
) -> dict[int, float]:
    """Percentile-based thresholds computed from the same training-set errors
    used for mean+3σ, for comparison only. Does not affect the threshold
    actually saved/used for anomaly judgment (see train_autoencoder)."""
    return {p: float(np.percentile(errors, p)) for p in percentiles}


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
    augmentation_seed: int = SEED,
) -> np.ndarray:
    kwargs = {}
    if raw_data_dir is not None:
        kwargs["raw_data_dir"] = raw_data_dir
    if synthetic_video_dir is not None:
        kwargs["synthetic_video_dir"] = synthetic_video_dir
    kwargs["include_synthetic"] = include_synthetic
    # Pin build_dataset's augmentation draws to the same seed used for model
    # training below, so "same seed" means the training DATA is identical too
    # (not just model init/shuffling) — defaults to SEED so existing callers
    # are unaffected.
    kwargs["augmentation_seed"] = augmentation_seed

    # Reuse build_dataset so landmark caching (FeatureCache) applies to training.
    bundle = build_dataset(**kwargs)

    # Autoencoder trains on normal data only (label == 0).
    normal_features = bundle.features[bundle.labels == 0]
    if normal_features.shape[0] == 0:
        raise ValueError(
            "No normal videos found. Autoencoder trains on normal data only."
        )

    if bundle.skipped:
        log_info(f"Skipped videos: {len(bundle.skipped)}")
    log_info(f"Normal training samples: {normal_features.shape[0]}")
    return normal_features.astype(np.float32)


def train_autoencoder(
    epochs: int = 100,
    lr: float = 1e-3,
    batch_size: int = 32,
    model_path=AUTOENCODER_PATH,
    seed: int = SEED,
) -> object:
    _require_torch()
    _require_sklearn()
    ensure_project_dirs()

    set_seed(seed)  # fix all randomness before touching data or the model

    features = _build_normal_features(augmentation_seed=seed)
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

    # Comparison only — logged for visibility, does not change the threshold
    # actually saved/used for anomaly judgment below.
    for percentile, value in compute_percentile_thresholds(errors).items():
        log_info(f"[comparison only] {percentile}th percentile threshold: {value:.6f}")

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
