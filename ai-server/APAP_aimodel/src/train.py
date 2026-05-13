from __future__ import annotations

import sys
from collections import Counter

import numpy as np

try:
    from sklearn.metrics import accuracy_score, classification_report
    from sklearn.model_selection import train_test_split
except ImportError as exc:  # pragma: no cover - depends on local environment
    accuracy_score = None
    classification_report = None
    train_test_split = None
    _SKLEARN_IMPORT_ERROR = exc
else:
    _SKLEARN_IMPORT_ERROR = None

from .dataset import build_dataset
from .model import create_model, save_model
from .utils import MODEL_PATH, ensure_project_dirs, format_path, log_error, log_info


LABEL_NAMES = {0: "normal", 1: "abnormal"}


def _require_training_dependencies() -> None:
    if train_test_split is None or accuracy_score is None or classification_report is None:
        raise RuntimeError(
            "Missing required package: scikit-learn. "
            "Install dependencies with `pip install -r requirements.txt`."
        ) from _SKLEARN_IMPORT_ERROR


def _validate_training_labels(labels: np.ndarray) -> None:
    label_counts = Counter(labels.tolist())
    if len(label_counts) < 2:
        readable = {
            LABEL_NAMES.get(label, str(label)): count
            for label, count in sorted(label_counts.items())
        }
        raise ValueError(
            "Training requires both normal and abnormal samples. "
            f"Current valid sample counts: {readable}."
        )

    too_small = {
        LABEL_NAMES.get(label, str(label)): count
        for label, count in sorted(label_counts.items())
        if count < 2
    }
    if too_small:
        raise ValueError(
            "Train/test split requires at least 2 valid videos per class. "
            f"Classes with insufficient samples: {too_small}."
        )


def _test_size_for(labels: np.ndarray) -> float:
    class_count = len(np.unique(labels))
    sample_count = len(labels)
    return max(0.25, class_count / sample_count)


def train_model() -> object:
    _require_training_dependencies()
    ensure_project_dirs()

    dataset = build_dataset()
    features = dataset.features
    labels = dataset.labels

    _validate_training_labels(labels)

    label_counts = Counter(labels.tolist())
    log_info(f"Valid samples: {len(labels)}")
    log_info(
        "Class counts: "
        + ", ".join(
            f"{LABEL_NAMES.get(label, label)}={count}"
            for label, count in sorted(label_counts.items())
        )
    )
    log_info(f"Feature shape: {features.shape}")
    if dataset.skipped:
        log_info(f"Skipped videos: {len(dataset.skipped)}")

    x_train, x_test, y_train, y_test = train_test_split(
        features,
        labels,
        test_size=_test_size_for(labels),
        random_state=42,
        stratify=labels,
    )

    log_info(f"Train samples: {len(y_train)}")
    log_info(f"Test samples: {len(y_test)}")

    model = create_model()
    model.fit(x_train, y_train)

    predictions = model.predict(x_test)
    accuracy = accuracy_score(y_test, predictions)

    print(f"Accuracy: {accuracy:.4f}")
    print(
        classification_report(
            y_test,
            predictions,
            labels=[0, 1],
            target_names=["normal", "abnormal"],
            zero_division=0,
        )
    )

    save_model(model, MODEL_PATH)
    log_info(f"Saved model: {format_path(MODEL_PATH)}")
    return model


def main() -> int:
    try:
        train_model()
    except Exception as exc:
        log_error(str(exc))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
