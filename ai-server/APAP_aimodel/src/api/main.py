from __future__ import annotations

from fastapi import FastAPI

from ..infer import predict_video
from ..utils import MODEL_PATH
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
    model_path = request.model_path or str(MODEL_PATH)

    try:
        result = predict_video(video_path=request.video_path, model_path=model_path)
    except FileNotFoundError as exc:
        return InferenceResponse(
            prediction=None,
            confidence=0.0,
            source=request.video_path,
            status="error",
            message=(
                f"{exc} If the model file is missing, run "
                "`python -m src.train` first."
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

    return InferenceResponse(
        prediction=result.label,
        confidence=result.confidence,
        source=request.video_path,
        status="success",
    )
