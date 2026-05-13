from __future__ import annotations

from pathlib import Path
from typing import Any

try:
    import joblib
except ImportError as exc:  # pragma: no cover - depends on local environment
    joblib = None
    _JOBLIB_IMPORT_ERROR = exc
else:
    _JOBLIB_IMPORT_ERROR = None

try:
    from sklearn.ensemble import RandomForestClassifier
except ImportError as exc:  # pragma: no cover - depends on local environment
    RandomForestClassifier = None
    _SKLEARN_IMPORT_ERROR = exc
else:
    _SKLEARN_IMPORT_ERROR = None

from .utils import ensure_dir


def _require_model_dependencies() -> None:
    missing = []
    if joblib is None:
        missing.append("joblib")
    if RandomForestClassifier is None:
        missing.append("scikit-learn")

    if missing:
        packages = ", ".join(missing)
        raise RuntimeError(
            f"Missing required package(s): {packages}. "
            "Install dependencies with `pip install -r requirements.txt`."
        ) from (_JOBLIB_IMPORT_ERROR or _SKLEARN_IMPORT_ERROR)


def create_model(random_state: int = 42) -> Any:
    _require_model_dependencies()
    return RandomForestClassifier(
        n_estimators=200,
        random_state=random_state,
        class_weight="balanced",
        n_jobs=-1,
    )


def save_model(model: Any, path: str | Path) -> Path:
    _require_model_dependencies()
    model_path = Path(path)
    ensure_dir(model_path.parent)
    joblib.dump(model, model_path)
    return model_path


def load_model(path: str | Path) -> Any:
    _require_model_dependencies()
    model_path = Path(path)
    if not model_path.exists():
        raise FileNotFoundError(
            f"Model file not found: {model_path}. "
            "Run `python -m src.train` first."
        )
    return joblib.load(model_path)
