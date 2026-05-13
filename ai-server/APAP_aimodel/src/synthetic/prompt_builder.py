from __future__ import annotations


def _join_or_none(values: list[str] | tuple[str, ...]) -> str:
    if not values:
        return "none"
    return ", ".join(values)


def build_video_prompt(scenario: dict) -> str:
    """Build a video generation prompt from a structured scenario dict."""
    label = scenario.get("label", "unknown")
    action_description = scenario.get("action_description", "")
    body_parts = _join_or_none(scenario.get("body_parts", []))
    objects = _join_or_none(scenario.get("objects", []))
    context = scenario.get("context", "public_area")
    duration_seconds = scenario.get("duration_seconds", 5)
    variations = _join_or_none(scenario.get("variations", []))

    return (
        "Generate a short CCTV-style training video for a normal/abnormal "
        "behavior classification dataset.\n"
        f"Label: {label}\n"
        f"Action: {action_description}\n"
        f"Context: {context}\n"
        f"Visible body parts and motion cues: {body_parts}\n"
        f"Relevant objects: {objects}\n"
        f"Duration: about {duration_seconds} seconds\n"
        f"Required variations: {variations}\n"
        "Camera style: fixed surveillance camera, realistic human motion, "
        "full body visible when possible, no cinematic cuts, no text overlay."
    )
