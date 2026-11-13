from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

try:
    import torch
    import torch.nn as nn
except ImportError as exc:
    torch = None
    nn = None
    _TORCH_IMPORT_ERROR = exc
else:
    _TORCH_IMPORT_ERROR = None

from ..utils import ensure_dir


def _require_torch() -> None:
    if torch is None:
        raise RuntimeError(
            "Missing required package: torch. "
            "Install with `pip install torch`."
        ) from _TORCH_IMPORT_ERROR


@dataclass
class AnomalyCheckpoint:
    """Bundles model weights, scaler, input dimension, and reconstruction threshold."""
    state_dict: dict
    scaler: Any          # sklearn StandardScaler fitted on training features
    input_dim: int
    threshold: float


class AutoencoderModel(nn.Module):
    def __init__(self, input_dim: int, hidden_dim: int = 16, bottleneck_dim: int = 8) -> None:
        super().__init__()
        self.encoder = nn.Sequential(
            nn.Linear(input_dim, hidden_dim),
            nn.ReLU(),
            nn.Linear(hidden_dim, bottleneck_dim),
            nn.ReLU(),
        )
        self.decoder = nn.Sequential(
            nn.Linear(bottleneck_dim, hidden_dim),
            nn.ReLU(),
            nn.Linear(hidden_dim, input_dim),
        )

    def forward(self, x):
        return self.decoder(self.encoder(x))


def create_autoencoder(input_dim: int) -> AutoencoderModel:
    _require_torch()
    return AutoencoderModel(input_dim=input_dim)


def save_autoencoder(
    model: AutoencoderModel,
    scaler: Any,
    threshold: float,
    path: str | Path,
) -> Path:
    _require_torch()
    model_path = Path(path)
    ensure_dir(model_path.parent)
    checkpoint = AnomalyCheckpoint(
        state_dict=model.state_dict(),
        scaler=scaler,
        input_dim=model.encoder[0].in_features,
        threshold=threshold,
    )
    torch.save(checkpoint, model_path)
    return model_path


def load_autoencoder(path: str | Path) -> tuple[AutoencoderModel, Any, float]:
    """Returns (model, scaler, threshold)."""
    _require_torch()
    model_path = Path(path)
    if not model_path.exists():
        raise FileNotFoundError(
            f"Autoencoder checkpoint not found: {model_path}. "
            "Run `python -m src.anomaly.train` first."
        )
    checkpoint: AnomalyCheckpoint = torch.load(model_path, map_location="cpu", weights_only=False)
    model = AutoencoderModel(input_dim=checkpoint.input_dim)
    model.load_state_dict(checkpoint.state_dict)
    model.eval()
    return model, checkpoint.scaler, checkpoint.threshold
