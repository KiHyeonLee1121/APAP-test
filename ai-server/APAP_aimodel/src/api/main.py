from __future__ import annotations

import os
from typing import Iterator

import cv2
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse

from ..anomaly.infer import predict_anomaly
from ..anomaly.realtime import draw_overlay, iter_live_results
from ..anomaly.train import AUTOENCODER_PATH
from .schemas import HealthResponse, InferenceResponse, VideoInferenceRequest


app = FastAPI(
    title="APAP AI Model Server",
    description="Central-server MVP inference API for APAP.",
    version="0.1.0",
)

# The web frontend loads the live stream directly from this server, so browser
# requests need to be allowed. This server holds no user data and sits behind
# the app's own network, so a permissive dev default is fine; restrict via
# ALLOWED_ORIGINS (comma-separated) when deploying somewhere public.
app.add_middleware(
    CORSMiddleware,
    allow_origins=[o for o in os.environ.get("ALLOWED_ORIGINS", "*").split(",") if o],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

MJPEG_BOUNDARY = "frame"

# Cap the streamed frame size: a phone-recorded 4K source would otherwise send
# multi-MB JPEGs per frame and stall the browser. Detection still runs on the
# full-resolution frame; only the streamed copy is scaled down.
STREAM_MAX_WIDTH = 960
JPEG_QUALITY = 80


def _downscale_for_stream(frame):
    height, width = frame.shape[:2]
    if width <= STREAM_MAX_WIDTH:
        return frame
    scale = STREAM_MAX_WIDTH / width
    return cv2.resize(
        frame,
        (STREAM_MAX_WIDTH, int(round(height * scale))),
        interpolation=cv2.INTER_AREA,
    )


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse()


def _mjpeg_frames(rtsp_url: str, model_path: str) -> Iterator[bytes]:
    """Annotated live frames as an MJPEG multipart stream.

    Reuses iter_live_results (the same generator the CLI demo consumes), so the
    browser view and the console output always show the same verdict.
    """
    for result in iter_live_results(rtsp_url=rtsp_url, model_path=model_path):
        # Scale first, then annotate, so the overlay is sized for what the
        # browser actually receives.
        frame = _downscale_for_stream(result.frame)
        draw_overlay(frame, result.label)
        encoded, buffer = cv2.imencode(
            ".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), JPEG_QUALITY]
        )
        if not encoded:
            continue
        yield (
            f"--{MJPEG_BOUNDARY}\r\n".encode()
            + b"Content-Type: image/jpeg\r\n\r\n"
            + buffer.tobytes()
            + b"\r\n"
        )


@app.get("/stream/live")
def stream_live() -> StreamingResponse:
    """Live camera view with NORMAL/ABNORMAL overlay, as MJPEG.

    Browsers can't play RTSP directly, so the server decodes the camera stream,
    runs anomaly detection, and re-serves annotated frames that an <img> tag can
    display. The camera URL comes from the RTSP_URL environment variable so
    credentials never appear in the page or in request logs.
    """
    rtsp_url = os.environ.get("RTSP_URL")
    if not rtsp_url:
        raise HTTPException(
            status_code=503,
            detail="RTSP_URL 환경변수가 설정되지 않았습니다. AI 서버 실행 시 지정하세요.",
        )

    return StreamingResponse(
        _mjpeg_frames(rtsp_url, str(AUTOENCODER_PATH)),
        media_type=f"multipart/x-mixed-replace; boundary={MJPEG_BOUNDARY}",
    )


@app.post("/predict/video", response_model=InferenceResponse)
def predict_video_endpoint(request: VideoInferenceRequest) -> InferenceResponse:
    model_path = request.model_path or str(AUTOENCODER_PATH)

    try:
        result = predict_anomaly(video_path=request.video_path, model_path=model_path)
    except FileNotFoundError as exc:
        return InferenceResponse(
            prediction=None,
            confidence=0.0,
            source=request.video_path,
            status="error",
            message=(
                f"{exc} If the model file is missing, run "
                "`python -m src.anomaly.train` first."
            ),
        )
    except Exception as exc:
        return InferenceResponse(
            prediction=None,
            confidence=0.0,
            source=request.video_path,
            status="error",
            message=str(exc),
        )

    # reconstruction_error / threshold as a 0~1-ish confidence signal for the backend's
    # severity bucketing (>=0.9 CRITICAL, >=0.75 HIGH, >=0.5 MEDIUM, else LOW).
    confidence = min(result.reconstruction_error / result.threshold, 1.0) if result.threshold else 0.0

    return InferenceResponse(
        prediction=result.label,
        confidence=confidence,
        source=request.video_path,
        status="success",
    )
