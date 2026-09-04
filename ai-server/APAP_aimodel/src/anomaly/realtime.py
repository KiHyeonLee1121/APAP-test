from __future__ import annotations

import argparse
import os
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterator

import numpy as np

try:
    import cv2
except ImportError as exc:  # pragma: no cover - depends on local environment
    cv2 = None
    _CV2_IMPORT_ERROR = exc
else:
    _CV2_IMPORT_ERROR = None

try:
    import mediapipe as mp
except ImportError as exc:  # pragma: no cover - depends on local environment
    mp = None
    _MEDIAPIPE_IMPORT_ERROR = exc
else:
    _MEDIAPIPE_IMPORT_ERROR = None

from ..extract_pose import _landmarks_to_array
from ..realtime.stream_buffer import FrameBuffer
from ..stream.video_source import VideoSource
from ..utils import log_error, log_info
from ..windowing import STRIDE_SECONDS, WINDOW_SECONDS
from .infer import compute_window_errors
from .model import load_autoencoder
from .train import AUTOENCODER_PATH

# Used only when the stream doesn't report a usable fps (some RTSP cameras
# report 0 via cv2.CAP_PROP_FPS). Tapo C320WS's substream (stream2) commonly
# runs around this rate; only affects window/stride sizing, not correctness
# of the anomaly logic itself.
DEFAULT_FALLBACK_FPS = 15.0


def _require_realtime_dependencies() -> None:
    missing = []
    if cv2 is None:
        missing.append("opencv-python")
    if mp is None:
        missing.append("mediapipe")

    if missing:
        packages = ", ".join(missing)
        raise RuntimeError(
            f"Missing required package(s): {packages}. "
            "Install dependencies with `pip install -r requirements.txt`."
        ) from (_CV2_IMPORT_ERROR or _MEDIAPIPE_IMPORT_ERROR)


def _resolve_fps(video_source: VideoSource) -> tuple[float, bool]:
    """Return (fps, used_fallback). Mirrors read_video_fps's unknown-fps handling."""
    fps = 0.0
    if video_source.capture is not None:
        try:
            fps = float(video_source.capture.get(cv2.CAP_PROP_FPS))
        except Exception:
            fps = 0.0

    if not np.isfinite(fps) or fps <= 0:
        return DEFAULT_FALLBACK_FPS, True
    return fps, False


def _reconnect(
    video_source: VideoSource,
    max_attempts: int,
    delay_seconds: float,
) -> bool:
    video_source.release()
    for attempt in range(1, max_attempts + 1):
        log_info(f"RTSP 재연결 시도 {attempt}/{max_attempts} (source={video_source.source})...")
        time.sleep(delay_seconds)
        try:
            if video_source.open():
                log_info("RTSP 재연결 성공.")
                return True
        except Exception as exc:
            log_error(f"재연결 중 오류: {exc}")
    return False


@dataclass
class LiveFrameResult:
    """One decoded frame plus the current anomaly verdict.

    ``error`` is set only on frames where a NEW window judgment was made
    (every stride); it is None on the frames in between, which simply carry
    the most recent ``label`` forward.
    """

    frame: np.ndarray  # raw BGR frame, not yet annotated
    label: str  # "warming_up" | "NORMAL" | "ABNORMAL"
    threshold: float
    error: float | None = None
    is_anomaly: bool = False


def iter_live_results(
    rtsp_url: str,
    model_path: str | Path = AUTOENCODER_PATH,
    duration_seconds: float | None = None,
    max_reconnect_attempts: int = 10,
    reconnect_delay_seconds: float = 1.0,
    min_detection_confidence: float = 0.5,
    min_tracking_confidence: float = 0.5,
    model_complexity: int = 1,
) -> Iterator[LiveFrameResult]:
    """Yield one LiveFrameResult per decoded frame from an RTSP/video source.

    Single source of truth for realtime detection: both the CLI
    (run_realtime_rtsp_inference) and the API's MJPEG stream consume this, so
    the console demo and the web view always agree. Cleans up the pose model
    and capture when the consumer stops iterating.
    """
    _require_realtime_dependencies()

    model, scaler, threshold = load_autoencoder(model_path)
    log_info(f"모델 로드 완료 (threshold={threshold:.6f})")

    video_source = VideoSource(rtsp_url)
    if not video_source.open():
        raise RuntimeError(f"RTSP 스트림을 열 수 없습니다: {rtsp_url}")

    fps, used_fallback = _resolve_fps(video_source)
    if used_fallback:
        log_info(f"스트림 fps를 확인할 수 없어 기본값 {DEFAULT_FALLBACK_FPS} fps로 대체합니다.")
    else:
        log_info(f"스트림 fps: {fps:.2f}")

    # Same formula split_into_windows() uses internally, so a full buffer is
    # always treated as exactly one window by compute_window_errors() below —
    # batch and realtime share the identical error computation, not a copy of it.
    window_frames = max(1, round(WINDOW_SECONDS * fps))
    stride_frames = max(1, round(STRIDE_SECONDS * fps))
    log_info(f"윈도우: {window_frames}프레임(~{WINDOW_SECONDS}s), stride: {stride_frames}프레임(~{STRIDE_SECONDS}s)")

    landmark_buffer: FrameBuffer[np.ndarray] = FrameBuffer(max_size=window_frames)
    pose = mp.solutions.pose.Pose(
        static_image_mode=False,
        model_complexity=model_complexity,
        enable_segmentation=False,
        min_detection_confidence=min_detection_confidence,
        min_tracking_confidence=min_tracking_confidence,
    )

    frames_since_prediction = 0
    consecutive_read_failures = 0
    start_time = time.monotonic()
    last_label = "warming_up"

    try:
        while True:
            if duration_seconds is not None and (time.monotonic() - start_time) >= duration_seconds:
                log_info(f"지정된 실행 시간({duration_seconds}s) 경과. 종료합니다.")
                return

            success, frame = video_source.read()
            if not success:
                consecutive_read_failures += 1
                log_error(f"프레임 읽기 실패 (연속 {consecutive_read_failures}회).")
                if not _reconnect(video_source, max_reconnect_attempts, reconnect_delay_seconds):
                    log_error("최대 재연결 시도 횟수를 초과했습니다. 종료합니다.")
                    return
                consecutive_read_failures = 0
                # Re-derive fps/window sizing in case the reconnected stream differs.
                fps, used_fallback = _resolve_fps(video_source)
                continue

            consecutive_read_failures = 0

            rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            rgb_frame.flags.writeable = False
            result = pose.process(rgb_frame)

            if result.pose_landmarks is None:
                yield LiveFrameResult(frame=frame, label=last_label, threshold=threshold)
                continue

            landmark_buffer.add_frame(_landmarks_to_array(result.pose_landmarks))
            frames_since_prediction += 1

            if landmark_buffer.is_ready() and frames_since_prediction >= stride_frames:
                frames_since_prediction = 0
                window = np.asarray(landmark_buffer.get_window(), dtype=np.float32)
                # Identical call batch inference uses (src/anomaly/infer.py):
                # a full-size buffer is windowed into exactly one window here.
                errors = compute_window_errors(window, fps, model, scaler)
                error = float(np.max(errors))
                is_anomaly = error > threshold
                last_label = "ABNORMAL" if is_anomaly else "NORMAL"
                yield LiveFrameResult(
                    frame=frame,
                    label=last_label,
                    threshold=threshold,
                    error=error,
                    is_anomaly=is_anomaly,
                )
                continue

            yield LiveFrameResult(frame=frame, label=last_label, threshold=threshold)
    finally:
        pose.close()
        video_source.release()


def run_realtime_rtsp_inference(
    rtsp_url: str,
    model_path: str | Path = AUTOENCODER_PATH,
    display: bool = False,
    duration_seconds: float | None = None,
    max_reconnect_attempts: int = 10,
    reconnect_delay_seconds: float = 1.0,
    min_detection_confidence: float = 0.5,
    min_tracking_confidence: float = 0.5,
    model_complexity: int = 1,
) -> None:
    results = iter_live_results(
        rtsp_url=rtsp_url,
        model_path=model_path,
        duration_seconds=duration_seconds,
        max_reconnect_attempts=max_reconnect_attempts,
        reconnect_delay_seconds=reconnect_delay_seconds,
        min_detection_confidence=min_detection_confidence,
        min_tracking_confidence=min_tracking_confidence,
        model_complexity=model_complexity,
    )

    try:
        for result in results:
            if result.error is not None:
                timestamp = datetime.now().strftime("%H:%M:%S")
                if result.is_anomaly:
                    print(
                        f"[ABNORMAL] 오차: {result.error:.4f} > threshold "
                        f"{result.threshold:.4f}, 시각: {timestamp}"
                    )
                else:
                    print(
                        f"[normal]   오차: {result.error:.4f} <= threshold "
                        f"{result.threshold:.4f}, 시각: {timestamp}"
                    )

            if display:
                draw_overlay(result.frame, result.label)
                if _show_and_check_quit(result.frame):
                    break
    finally:
        results.close()
        if display and cv2 is not None:
            cv2.destroyAllWindows()


def draw_overlay(frame: np.ndarray, label: str) -> None:
    """Draw the current verdict on the frame, sized relative to the frame.

    A fixed font scale is unreadable on a 4K frame and oversized on a 360p one,
    so scale with frame height and draw a filled backing box for contrast.
    """
    color = (0, 0, 255) if label == "ABNORMAL" else (0, 200, 0)
    height = frame.shape[0]
    scale = max(0.6, height / 720)
    thickness = max(2, int(round(scale * 2)))

    (text_w, text_h), baseline = cv2.getTextSize(
        label, cv2.FONT_HERSHEY_SIMPLEX, scale, thickness
    )
    pad = int(round(8 * scale))
    cv2.rectangle(
        frame,
        (pad, pad),
        (pad * 3 + text_w, pad * 3 + text_h + baseline),
        (0, 0, 0),
        thickness=-1,
    )
    cv2.putText(
        frame,
        label,
        (pad * 2, pad * 2 + text_h),
        cv2.FONT_HERSHEY_SIMPLEX,
        scale,
        color,
        thickness,
        cv2.LINE_AA,
    )


def _show_and_check_quit(frame: np.ndarray) -> bool:
    cv2.imshow("APAP realtime anomaly detection", frame)
    return cv2.waitKey(1) & 0xFF == ord("q")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run RTSP realtime Autoencoder-based anomaly detection."
    )
    parser.add_argument(
        "--rtsp-url",
        default=None,
        help=(
            "RTSP stream URL (e.g. rtsp://user:pass@IP:554/stream2). "
            "Falls back to the RTSP_URL environment variable if omitted — "
            "never hardcode camera credentials in source/version control."
        ),
    )
    parser.add_argument(
        "--model",
        default=str(AUTOENCODER_PATH),
        help="Path to a trained autoencoder checkpoint (.pt).",
    )
    parser.add_argument(
        "--display",
        action="store_true",
        help="Show a window with the live frame and NORMAL/ABNORMAL overlay.",
    )
    parser.add_argument(
        "--duration-seconds",
        type=float,
        default=None,
        help="Optional: stop automatically after this many seconds (useful for smoke tests).",
    )
    parser.add_argument(
        "--max-reconnect-attempts",
        type=int,
        default=10,
        help="Max consecutive reconnect attempts before giving up.",
    )
    parser.add_argument(
        "--reconnect-delay-seconds",
        type=float,
        default=1.0,
        help="Delay between reconnect attempts.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    rtsp_url = args.rtsp_url or os.environ.get("RTSP_URL")
    if not rtsp_url:
        log_error(
            "RTSP URL이 지정되지 않았습니다. --rtsp-url 인자 또는 RTSP_URL 환경변수로 전달하세요."
        )
        return 1

    try:
        run_realtime_rtsp_inference(
            rtsp_url=rtsp_url,
            model_path=args.model,
            display=args.display,
            duration_seconds=args.duration_seconds,
            max_reconnect_attempts=args.max_reconnect_attempts,
            reconnect_delay_seconds=args.reconnect_delay_seconds,
        )
    except Exception as exc:
        log_error(str(exc))
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
