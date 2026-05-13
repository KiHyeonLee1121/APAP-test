from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from ..utils import ensure_dir


@dataclass
class VideoGenerationResult:
    prompt: str
    output_path: str
    status: str
    message: str


class BaseVideoGenerator:
    """Interface for future video generation backends."""

    def generate(self, prompt: str, output_path: str | Path) -> VideoGenerationResult:
        raise NotImplementedError


class MockVideoGenerator(BaseVideoGenerator):
    """MVP generator that reserves a target path without creating an mp4."""

    def generate(self, prompt: str, output_path: str | Path) -> VideoGenerationResult:
        target_path = Path(output_path)
        ensure_dir(target_path.parent)

        return VideoGenerationResult(
            prompt=prompt,
            output_path=str(target_path),
            status="prompt_generated",
            message=(
                "Mock generator did not create a video file. "
                "Connect a real video generation backend later."
            ),
        )


def generate_video(prompt: str, output_path: str) -> str:
    """Compatibility function for the MVP pipeline.

    The current implementation does not create an mp4 file. It returns the
    expected path where a future generated video should be saved.
    """
    result = MockVideoGenerator().generate(prompt, output_path)
    return result.output_path
