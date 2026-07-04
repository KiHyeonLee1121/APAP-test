from __future__ import annotations

import argparse
import sys
from pathlib import Path

from ..stream.video_source import VideoSource
from ..utils import log_error, log_info
from .realtime_processor import RealtimeAnomalyProcessor
from .train import AUTOENCODER_PATH


def run_realtime_anomaly_inference(
    source: str,
    model_path: str | Path = AUTOENCODER_PATH,
    window_size: int = 30,
    frame_skip: int = 1,
    prediction_interval: int = 10,
    max_frames: int | None = None,
    camera_id: str = "default",
    print_status: bool = False,
) -> None:
    video_source = VideoSource(source)
    if not video_source.open():
        raise RuntimeError(f"Could not open video source: {source}")

    try:
        with RealtimeAnomalyProcessor(
            model_path=model_path,
            window_size=window_size,
            frame_skip=frame_skip,
            prediction_interval=prediction_interval,
            camera_id=camera_id,
        ) as processor:
            frame_index = 0
            while True:
                success, frame = video_source.read()
                if not success:
                    break

                frame_index += 1
                result = processor.process_frame(frame)

                if result and result["status"] == "predicted":
                    flag = "⚠️ ANOMALY" if result["is_anomaly"] else "ok"
                    print(
                        f"[{flag}] "
                        f"Prediction: {result['prediction']} | "
                        f"Error: {result['reconstruction_error']:.6f} "
                        f"(threshold: {result['threshold']:.6f}) | "
                        f"Frame: {result['frames_seen']} | "
                        f"Buffer: {result['buffer_size']}/{result['window_size']}"
                    )
                elif print_status and result:
                    print(
                        "Status: "
                        f"{result['status']} | "
                        f"Frame: {result['frames_seen']} | "
                        f"Buffer: {result['buffer_size']}/{result['window_size']}"
                    )

                if max_frames is not None and frame_index >= max_frames:
                    break

            log_info(f"Finished realtime anomaly inference. Frames read: {frame_index}")
    finally:
        video_source.release()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run APAP realtime Autoencoder-based anomaly detection."
    )
    parser.add_argument(
        "--source",
        required=True,
        help="Video file path or webcam index. Use --source 0 for default webcam.",
    )
    parser.add_argument(
        "--model",
        default=str(AUTOENCODER_PATH),
        help="Path to a trained autoencoder checkpoint (.pt).",
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
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    try:
        run_realtime_anomaly_inference(
            source=args.source,
            model_path=args.model,
            window_size=args.window_size,
            frame_skip=args.frame_skip,
            prediction_interval=args.prediction_interval,
            max_frames=args.max_frames,
            camera_id=args.camera_id,
            print_status=args.print_status,
        )
    except Exception as exc:
        log_error(str(exc))
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
