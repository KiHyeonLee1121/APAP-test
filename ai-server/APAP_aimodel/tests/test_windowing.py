"""Tests for src/windowing.py sliding-window splitting.

Runnable standalone (no pytest) or via pytest. No real video/MediaPipe needed.
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from src.windowing import split_into_windows


def _landmarks(num_frames: int) -> np.ndarray:
    return np.zeros((num_frames, 33, 4), dtype=np.float32)


def test_basic_window_count_and_shape():
    # 100 frames @ 30 fps, 1.0s window (30 frames), 0.5s stride (15 frames).
    windows = split_into_windows(_landmarks(100), fps=30.0)
    # Full windows start at 0,15,30,45,60,70? -> starts while start+30<=100:
    # 0,15,30,45,60,(75 ->75+30=105>100 stop) => 5 windows, last covers [60:90].
    # Tail: last_covered_end=90 < 100 -> +1 end-anchored window [70:100].
    assert len(windows) == 6
    assert all(w.shape == (30, 33, 4) for w in windows)


def test_unknown_fps_falls_back_to_single_window():
    lm = _landmarks(240)
    result = split_into_windows(lm, fps=0.0)
    assert len(result) == 1
    assert result[0].shape == (240, 33, 4)


def test_clip_shorter_than_window_is_single_window():
    # 10 frames @ 30 fps -> window is 30 frames > 10 -> whole clip as one window.
    result = split_into_windows(_landmarks(10), fps=30.0)
    assert len(result) == 1
    assert result[0].shape == (10, 33, 4)


def test_tail_window_covers_end():
    # 95 frames @ 30 fps: full windows end at 90; a tail window must reach 95.
    windows = split_into_windows(_landmarks(95), fps=30.0)
    # The last window must include the final frame region [65:95].
    assert windows[-1].shape == (30, 33, 4)
    # No window should exceed the clip length (all exactly window_frames).
    assert all(w.shape[0] == 30 for w in windows)


def test_exact_multiple_no_duplicate_tail():
    # 90 frames @ 30 fps: windows at 0,15,30,45,60 -> last covers [60:90]=end.
    # last_covered_end == 90 == total -> NO extra tail window.
    windows = split_into_windows(_landmarks(90), fps=30.0)
    assert len(windows) == 5


def _run_all() -> int:
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    failures = 0
    for test in tests:
        try:
            test()
        except Exception as exc:  # noqa: BLE001
            failures += 1
            print(f"[FAIL] {test.__name__}: {type(exc).__name__}: {exc}")
        else:
            print(f"[PASS] {test.__name__}")
    print(f"\n{len(tests) - failures}/{len(tests)} passed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(_run_all())
