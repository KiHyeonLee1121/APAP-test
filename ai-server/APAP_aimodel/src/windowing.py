from __future__ import annotations

import numpy as np


# Sliding-window defaults, in seconds. Frame counts are derived per-video as
# (seconds * fps), never hardcoded, so clips with different fps window
# consistently in wall-clock time. Tune these to trade temporal resolution
# (shorter window = better at catching brief anomalies) against per-window
# stability (longer window = smoother summary statistics).
WINDOW_SECONDS = 1.0
STRIDE_SECONDS = 0.5


def split_into_windows(
    landmarks: np.ndarray,
    fps: float,
    window_seconds: float = WINDOW_SECONDS,
    stride_seconds: float = STRIDE_SECONDS,
) -> list[np.ndarray]:
    """Slice (T, 33, 4) landmarks into fixed-duration sliding windows.

    Each window is ``(window_frames, 33, 4)`` where ``window_frames`` is derived
    from the clip's own fps. If fps is unknown (<= 0) or the clip is shorter than
    one window, the whole clip is returned as a single window so short videos and
    unknown-fps sources still yield exactly one feature row.
    """
    total_frames = landmarks.shape[0]
    if fps <= 0:
        return [landmarks]

    window_frames = max(1, round(window_seconds * fps))
    stride_frames = max(1, round(stride_seconds * fps))

    if total_frames <= window_frames:
        return [landmarks]

    windows: list[np.ndarray] = []
    start = 0
    while start + window_frames <= total_frames:
        windows.append(landmarks[start : start + window_frames])
        start += stride_frames

    # Cover the tail: if the last full window ended before the clip did, add one
    # window anchored at the end so an anomaly in the final < stride_seconds is
    # not dropped.
    last_covered_end = (start - stride_frames) + window_frames
    if last_covered_end < total_frames:
        windows.append(landmarks[total_frames - window_frames : total_frames])

    return windows


def window_time_bounds(
    window_index: int,
    fps: float,
    stride_seconds: float = STRIDE_SECONDS,
    window_seconds: float = WINDOW_SECONDS,
) -> tuple[float, float]:
    """Return the (start_sec, end_sec) of a window by its index.

    Approximate for the tail window (which is anchored at the clip end), but
    close enough to point a human at the right moment in the clip.
    """
    if fps <= 0:
        return (0.0, 0.0)
    start_sec = window_index * stride_seconds
    return (start_sec, start_sec + window_seconds)
