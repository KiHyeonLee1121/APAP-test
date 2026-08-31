from __future__ import annotations

from fastapi import FastAPI

from ..anomaly.infer import predict_anomaly
from ..anomaly.train import AUTOENCODER_PATH
from .schemas import HealthResponse, InferenceResponse, VideoInferenceRequest


app = FastAPI(
    title="APAP AI Model Server",
    description="Central-server MVP inference API for APAP.",
    version="0.1.0",
)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse()


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
