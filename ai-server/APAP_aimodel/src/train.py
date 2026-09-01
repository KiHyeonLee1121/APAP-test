from __future__ import annotations

import sys
from collections import Counter

import numpy as np

try:
    from sklearn.metrics import accuracy_score, classification_report
    from sklearn.model_selection import StratifiedGroupKFold
except ImportError as exc:  # pragma: no cover - depends on local environment
    accuracy_score = None
    classification_report = None
    StratifiedGroupKFold = None
    _SKLEARN_IMPORT_ERROR = exc
else:
    _SKLEARN_IMPORT_ERROR = None

from .dataset import build_dataset
from .model import create_model, save_model
from .utils import MODEL_PATH, ensure_project_dirs, format_path, log_error, log_info


LABEL_NAMES = {0: "normal", 1: "abnormal"}


def _require_training_dependencies() -> None:
    if StratifiedGroupKFold is None or accuracy_score is None or classification_report is None:
        raise RuntimeError(
            "Missing required package: scikit-learn. "
            "Install dependencies with `pip install -r requirements.txt`."
        ) from _SKLEARN_IMPORT_ERROR


def _videos_per_class(labels: np.ndarray, groups: np.ndarray) -> dict[str, int]:
    return {
        LABEL_NAMES.get(int(label), str(label)): len(set(groups[labels == label]))
        for label in np.unique(labels)
    }


def _validate_training_labels(labels: np.ndarray, groups: np.ndarray) -> None:
    if len(np.unique(labels)) < 2:
        readable = {
            LABEL_NAMES.get(label, str(label)): count
            for label, count in sorted(Counter(labels.tolist()).items())
        }
        raise ValueError(
            "Training requires both normal and abnormal samples. "
            f"Current valid sample counts: {readable}."
        )

    # Windowing makes one video expand into many rows, so validity must be
    # measured in videos (groups), not rows: a group-aware split needs at least
    # two videos per class so a whole video can be held out for testing.
    videos = _videos_per_class(labels, groups)
    too_small = {name: count for name, count in videos.items() if count < 2}
    if too_small:
        raise ValueError(
            "Group-aware train/test split requires at least 2 valid videos per "
            f"class (so a whole video can be held out). Videos per class: {videos}."
        )


def _n_splits_for(labels: np.ndarray, groups: np.ndarray) -> int:
    """Pick K so the first fold holds out ~25% for test, but never more folds
    than the smallest class has videos (each fold must hold out a whole video
    per class)."""
    min_videos = min(_videos_per_class(labels, groups).values())
    return max(2, min(4, min_videos))


def _select_split(splitter, features, labels, groups):
    """Return the first fold whose TEST set contains both classes.

    With few video groups, StratifiedGroupKFold's first fold can land a
    single-class test set, which makes accuracy meaningless. Scanning for a fold
    with both classes keeps the metric interpretable while preserving the group
    (no-leakage) guarantee. Falls back to the first fold if none qualifies."""
    folds = list(splitter.split(features, labels, groups))
    for train_idx, test_idx in folds:
        if len(np.unique(labels[test_idx])) == 2:
            return train_idx, test_idx
    return folds[0]


def train_model() -> object:
    _require_training_dependencies()
    ensure_project_dirs()

    dataset = build_dataset()
    features = dataset.features
    labels = dataset.labels
    # One group per source video; windows from the same video share a group so
    # the split below never places windows of one video in both train and test.
    groups = np.asarray([str(path) for path in dataset.paths])

    _validate_training_labels(labels, groups)

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

    # Group-aware, stratified split: keeps class balance (like the old
    # stratify=labels) while guaranteeing no video spans train and test, so the
    # reported accuracy is not inflated by windows leaking across the split.
    splitter = StratifiedGroupKFold(
        n_splits=_n_splits_for(labels, groups),
        shuffle=True,
        random_state=42,
    )
    train_idx, test_idx = _select_split(splitter, features, labels, groups)

    # Invariant: the group-aware split holds whole videos out (no leakage).
    assert set(groups[train_idx]).isdisjoint(groups[test_idx])

    x_train, x_test = features[train_idx], features[test_idx]
    y_train, y_test = labels[train_idx], labels[test_idx]

    log_info(f"Train samples: {len(y_train)} (videos: {len(set(groups[train_idx]))})")
    log_info(f"Test samples: {len(y_test)} (videos: {len(set(groups[test_idx]))})")

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
