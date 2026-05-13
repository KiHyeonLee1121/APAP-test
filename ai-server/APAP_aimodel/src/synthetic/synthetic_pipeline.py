from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

from .prompt_builder import build_video_prompt
from .scenario_parser import parse_user_action
from .video_generator import MockVideoGenerator
from ..utils import (
    SYNTHETIC_METADATA_DIR,
    SYNTHETIC_VIDEO_DIR,
    ensure_dir,
    format_path,
    log_error,
    log_info,
)


def _make_scenario_id() -> str:
    return datetime.now().strftime("scenario_%Y%m%d_%H%M%S_%f")


def _metadata_path_for(scenario_id: str) -> Path:
    return SYNTHETIC_METADATA_DIR / f"{scenario_id}.json"


def _expected_video_path_for(scenario_id: str, label: str) -> Path:
    return SYNTHETIC_VIDEO_DIR / label / f"{scenario_id}.mp4"


def create_synthetic_metadata(text: str, label: str) -> dict:
    scenario_id = _make_scenario_id()
    parsed_scenario = parse_user_action(text=text, label=label)
    generated_prompt = build_video_prompt(parsed_scenario)
    expected_video_path = _expected_video_path_for(
        scenario_id=scenario_id,
        label=parsed_scenario["label"],
    )

    generator = MockVideoGenerator()
    generation_result = generator.generate(
        prompt=generated_prompt,
        output_path=expected_video_path,
    )

    metadata = {
        "scenario_id": scenario_id,
        "label": parsed_scenario["label"],
        "user_text": text,
        "parsed_scenario": parsed_scenario,
        "generated_prompt": generated_prompt,
        "expected_video_path": str(expected_video_path),
        "source": "synthetic",
        "status": generation_result.status,
    }

    ensure_dir(SYNTHETIC_METADATA_DIR)
    metadata_path = _metadata_path_for(scenario_id)
    metadata_path.write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    metadata_with_path = dict(metadata)
    metadata_with_path["metadata_path"] = str(metadata_path)
    return metadata_with_path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create APAP synthetic scenario prompt metadata."
    )
    parser.add_argument(
        "--text",
        required=True,
        help="User action description.",
    )
    parser.add_argument(
        "--label",
        required=True,
        choices=["normal", "abnormal"],
        help="Scenario label.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    try:
        metadata = create_synthetic_metadata(text=args.text, label=args.label)
    except Exception as exc:
        log_error(str(exc))
        return 1

    log_info(f"Metadata saved: {format_path(metadata['metadata_path'])}")
    log_info(f"Expected video path: {format_path(metadata['expected_video_path'])}")
    print(f"Status: {metadata['status']}")
    print("Generated prompt:")
    print(metadata["generated_prompt"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
