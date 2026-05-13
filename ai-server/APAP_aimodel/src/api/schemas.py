from __future__ import annotations

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    service: str = "apap-ai-model"
    status: str = "ok"


class VideoInferenceRequest(BaseModel):
    video_path: str = Field(..., description="Path to a local mp4 video file.")
    model_path: str | None = Field(
        default=None,
        description="Optional path to a trained model checkpoint.",
    )


class InferenceResponse(BaseModel):
    prediction: str | None = None
    confidence: float = 0.0
    source: str
    status: str
    message: str | None = None
