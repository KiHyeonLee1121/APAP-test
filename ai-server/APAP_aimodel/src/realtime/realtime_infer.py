from __future__ import annotations

import argparse
import sys
from pathlib import Path

from ..utils import MODEL_PATH, log_error, log_info
from ..stream.video_source import VideoSource
from .realtime_processor import RealtimePoseProcessor


def run_realtime_inference(
    source: str,
    model_path: str | Path = MODEL_PATH,
    window_size: int = 30,
    frame_skip: int = 1,
    prediction_interval: int = 10,
    max_frames: int | None = None,
    camera_id: str = "default",
    print_status: bool = False,
    use_object_detection: bool = False,
    use_tracking: bool = False,
    use_object_features_for_prediction: bool = False,
    yolo_model_path: str = "yolov8n.pt",
    yolo_confidence_threshold: float = 0.25,
) -> None:
    video_source = VideoSource(source)
    if not video_source.open():
        raise RuntimeError(f"Could not open video source: {source}")

    try:
        with RealtimePoseProcessor(
            model_path=model_path,
            window_size=window_size,
            frame_skip=frame_skip,
            prediction_interval=prediction_interval,
            camera_id=camera_id,
            use_object_detection=use_object_detection,
            use_tracking=use_tracking,
            use_object_features_for_prediction=use_object_features_for_prediction,
            yolo_model_path=yolo_model_path,
            yolo_confidence_threshold=yolo_confidence_threshold,
        ) as processor:
            frame_index = 0
            while True:
                success, frame = video_source.read()
                if not success:
                    break

                frame_index += 1
                result = processor.process_frame(frame)

                if result and result["status"] == "predicted":
                    print(
                        "Prediction: "
                        f"{result['prediction']} | "
                        f"Confidence: {result['confidence']:.2f} | "
                        f"Frame: {result['frames_seen']} | "
                        f"Buffer: {result['buffer_size']}/{result['window_size']}"
                    )
                    if result.get("object_error"):
                        print(f"Object detection warning: {result['object_error']}")
                elif print_status and result:
                    print(
                        "Status: "
                        f"{result['status']} | "
                        f"Frame: {result['frames_seen']} | "
                        f"Buffer: {result['buffer_size']}/{result['window_size']}"
                    )

                if max_frames is not None and frame_index >= max_frames:
                    break

            log_info(f"Finished realtime inference. Frames read: {frame_index}")
    finally:
        video_source.release()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run APAP realtime sliding-window inference."
    )
    parser.add_argument(
        "--source",
        required=True,
        help="Video file path or webcam index. Use --source 0 for default webcam.",
    )
    parser.add_argument(
        "--model",
        default=str(MODEL_PATH),
        help="Path to a trained model checkpoint.",
    )
    parser.add_argument(
        "--window-size",
        type=int,
        default=30,
        help="Number of detected pose frames used for each prediction window.",
    )
    parser.add_argument(
        "--frame-skip",
        type=int,
        default=1,
        help="Process one frame every N frames.",
    )
    parser.add_argument(
        "--prediction-interval",
        type=int,
        default=10,
        help="Run prediction every N processed frames after the buffer is ready.",
    )
    parser.add_argument(
        "--max-frames",
        type=int,
        default=None,
        help="Optional maximum number of frames to read before exiting.",
    )
    parser.add_argument(
        "--camera-id",
        default="default",
        help="Logical camera id used in returned result dictionaries.",
    )
    parser.add_argument(
        "--print-status",
        action="store_true",
        help="Print warming_up and no_pose statuses as well as predictions.",
    )
    parser.add_argument(
        "--use-object-detection",
        action="store_true",
        help="Enable optional YOLO object detection.",
    )
    parser.add_argument(
        "--use-tracking",
        action="store_true",
        help="Enable optional dummy tracking interface after detection.",
    )
    parser.add_argument(
        "--use-object-features-for-prediction",
        action="store_true",
        help=(
            "Append object features to pose features before prediction. "
            "Use only with a model trained on the combined feature shape."
        ),
    )
    parser.add_argument(
        "--yolo-model",
        default="yolov8n.pt",
        help="YOLO model path or name used when object detection is enabled.",
    )
    parser.add_argument(
        "--yolo-confidence",
        type=float,
        default=0.25,
        help="Confidence threshold for optional YOLO detections.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    try:
        run_realtime_inference(
            source=args.source,
            model_path=args.model,
            window_size=args.window_size,
            frame_skip=args.frame_skip,
            prediction_interval=args.prediction_interval,
            max_frames=args.max_frames,
            camera_id=args.camera_id,
            print_status=args.print_status,
            use_object_detection=args.use_object_detection,
            use_tracking=args.use_tracking,
            use_object_features_for_prediction=args.use_object_features_for_prediction,
            yolo_model_path=args.yolo_model,
            yolo_confidence_threshold=args.yolo_confidence,
        )
    except Exception as exc:
        log_error(str(exc))
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
