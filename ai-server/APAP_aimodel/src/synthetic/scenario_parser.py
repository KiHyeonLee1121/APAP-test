from __future__ import annotations

import re


SUPPORTED_LABELS = {"normal", "abnormal"}


def _normalize_label(label: str) -> str:
    normalized = label.strip().lower()
    if normalized not in SUPPORTED_LABELS:
        raise ValueError(
            "Label must be either 'normal' or 'abnormal'. "
            f"Got: {label!r}."
        )
    return normalized


def _contains_any(text: str, keywords: tuple[str, ...]) -> bool:
    return any(keyword.lower() in text.lower() for keyword in keywords)


def _unique(values: list[str]) -> list[str]:
    return list(dict.fromkeys(values))


def _infer_body_parts(text: str) -> list[str]:
    body_parts: list[str] = []

    if _contains_any(text, ("손", "손목", "팔", "주머니", "hand", "arm", "wrist")):
        body_parts.append("hand")
    if _contains_any(text, ("머리", "고개", "시선", "두리번", "주변", "head", "gaze")):
        body_parts.append("head")
    if _contains_any(text, ("상체", "몸", "허리", "기울", "upper body", "torso")):
        body_parts.append("upper_body")
    if _contains_any(text, ("다리", "무릎", "발", "걷", "뛰", "leg", "knee", "foot")):
        body_parts.append("leg")
    if _contains_any(text, ("표정", "얼굴", "불안", "face", "expression")):
        body_parts.append("face")

    if not body_parts:
        body_parts.append("upper_body")

    return _unique(body_parts)


def _infer_objects(text: str) -> list[str]:
    objects: list[str] = []

    object_rules = (
        (("주머니", "pocket"), "pocket"),
        (("가방", "bag"), "bag"),
        (("atm", "ATM"), "atm"),
        (("난간", "railing"), "railing"),
        (("식권", "ticket"), "meal_ticket"),
        (("휴대폰", "핸드폰", "phone"), "phone"),
        (("문", "door"), "door"),
    )

    for keywords, object_name in object_rules:
        if _contains_any(text, keywords):
            objects.append(object_name)

    return _unique(objects)


def _infer_context(text: str) -> str:
    if _contains_any(text, ("atm", "ATM")):
        return "atm_area"
    if _contains_any(text, ("난간", "railing")):
        return "near_railing"
    if _contains_any(text, ("식권", "식당", "급식", "줄", "queue", "ticket")):
        return "cafeteria_queue"
    if _contains_any(text, ("복도", "corridor")):
        return "corridor"
    if _contains_any(text, ("매장", "상점", "shop", "store")):
        return "store"
    return "public_area"


def _infer_duration_seconds(text: str) -> int:
    match = re.search(r"(\d+)\s*초", text)
    if not match:
        return 5

    duration = int(match.group(1))
    return max(1, min(duration, 60))


def parse_user_action(text: str, label: str) -> dict:
    """Convert a user action description into a structured scenario.

    This is a rule-based MVP parser. It keeps the same interface that a future
    LLM-backed parser can implement.
    """
    user_text = text.strip()
    if not user_text:
        raise ValueError("Action description text must not be empty.")

    normalized_label = _normalize_label(label)

    return {
        "label": normalized_label,
        "action_description": user_text,
        "body_parts": _infer_body_parts(user_text),
        "objects": _infer_objects(user_text),
        "context": _infer_context(user_text),
        "duration_seconds": _infer_duration_seconds(user_text),
        "variations": [
            "different camera angle",
            "different lighting",
            "different background",
        ],
    }
