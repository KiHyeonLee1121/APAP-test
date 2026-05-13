from __future__ import annotations

from pathlib import Path
from typing import Any


DEFAULT_YOLO_MODEL = "yolov8n.pt"

APAP_OBJECT_ALIASES = {
    "person": "person",
    "backpack": "bag",
    "handbag": "bag",
    "suitcase": "bag",
    "bag": "bag",
    "atm": "atm",
    "railing": "railing",
    "product": "product",
}


def _load_yolo_class():
    try:
        from ultralytics import YOLO
    except ImportError as exc:  # pragma: no cover - depends on local environment
        raise RuntimeError(
            "Missing required package: ultralytics. "
            "Install dependencies with `pip install -r requirements.txt`."
        ) from exc
    return YOLO


def normalize_class_name(class_name: str) -> str:
    return APAP_OBJECT_ALIASES.get(class_name.lower(), class_name.lower())


class YOLODetector:
    """Lazy-loaded Ultralytics YOLO object detector."""

    def __init__(
        self,
        model_path: str | Path = DEFAULT_YOLO_MODEL,
        confidence_threshold: float = 0.25,
        target_classes: set[str] | None = None,
    ) -> None:
        if not 0.0 <= confidence_threshold <= 1.0:
            raise ValueError("confidence_threshold must be between 0 and 1.")

        self.model_path = str(model_path)
        self.confidence_threshold = confidence_threshold
        self.target_classes = (
            {normalize_class_name(name) for name in target_classes}
            if target_classes
            else None
        )
        self._model: Any | None = None

    def _load_model(self) -> Any:
        if self._model is None:
            YOLO = _load_yolo_class()
            try:
                self._model = YOLO(self.model_path)
            except Exception as exc:
                raise RuntimeError(
                    "Could not load YOLO model. Check the model path or network "
                    f"availability for default model download: {self.model_path}"
                ) from exc
        return self._model

    def detect(self, frame) -> list[dict]:
        model = self._load_model()
        results = model(frame, verbose=False)
        detections: list[dict] = []

        for result in results:
            names = getattr(result, "names", {})
            boxes = getattr(result, "boxes", None)
            if boxes is None:
                continue

            for box in boxes:
                class_id = int(box.cls[0].item())
                confidence = float(box.conf[0].item())
                if confidence < self.confidence_threshold:
                    continue

                raw_class_name = str(names.get(class_id, class_id))
                class_name = normalize_class_name(raw_class_name)
                if self.target_classes and class_name not in self.target_classes:
                    continue

                bbox = [float(value) for value in box.xyxy[0].tolist()]
                detections.append(
                    {
                        "class_id": class_id,
                        "class_name": class_name,
                        "raw_class_name": raw_class_name,
                        "confidence": confidence,
                        "bbox": bbox,
                    }
                )

        return detections
