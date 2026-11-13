"""Tests for the live-detection alert-worthiness policy (update_alert_state).

Covers exactly the scenarios discussed with the user before implementing:
  1. An isolated 1-2 window false positive during normal activity never fires.
  2. A real sustained event fires exactly once (not once per window).
  3. A single noise "NORMAL" blip in the middle of a real event does NOT
     split it into two alerts (the short reflag cooldown absorbs it).
  4. A genuinely new event, well after the cooldown window, fires normally —
     it is not silently suppressed by an unrelated earlier alert.

No camera/model needed: update_alert_state is a pure state transition.
Runnable standalone (no pytest) or via pytest.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from src.anomaly.realtime import (
    CONSECUTIVE_ABNORMAL_THRESHOLD,
    REFLAG_COOLDOWN_SECONDS,
    AlertState,
    update_alert_state,
)


def _feed(labels: list[bool], state: AlertState, times: list[float]) -> list[bool]:
    """Feed a sequence of is_anomaly values at the given monotonic times."""
    assert len(labels) == len(times)
    return [update_alert_state(label, state, t) for label, t in zip(labels, times)]


def test_isolated_false_positive_never_fires():
    # normal, normal, ABNORMAL, ABNORMAL, normal, normal — never reaches the
    # consecutive threshold, so it must never notify.
    state = AlertState()
    labels = [False, False, True, True, False, False, False]
    times = [float(i) for i in range(len(labels))]
    results = _feed(labels, state, times)
    assert not any(results), f"expected no notification, got {results}"


def test_sustained_event_fires_exactly_once():
    # A long, clean abnormal run: threshold crossed once, then must NOT
    # re-fire on every subsequent window for the rest of the same event.
    state = AlertState()
    run_length = CONSECUTIVE_ABNORMAL_THRESHOLD + 20
    labels = [True] * run_length
    times = [i * 0.5 for i in range(run_length)]  # 0.5s stride, matches realtime.py
    results = _feed(labels, state, times)

    assert sum(results) == 1, f"expected exactly one notification, got {sum(results)}"
    fire_index = results.index(True)
    assert fire_index == CONSECUTIVE_ABNORMAL_THRESHOLD - 1, (
        f"expected the fire on the {CONSECUTIVE_ABNORMAL_THRESHOLD}th consecutive "
        f"ABNORMAL window (index {CONSECUTIVE_ABNORMAL_THRESHOLD - 1}), got index {fire_index}"
    )


def test_single_normal_blip_mid_event_does_not_double_fire():
    # Real event: 5 abnormal (fires) -> 1 noise "normal" blip -> abnormal
    # resumes immediately. The resumed run reaches the threshold again well
    # within REFLAG_COOLDOWN_SECONDS of the first fire, so it must NOT re-fire.
    state = AlertState()
    labels = (
        [True] * CONSECUTIVE_ABNORMAL_THRESHOLD  # fires once here
        + [False]                                 # single noise blip
        + [True] * CONSECUTIVE_ABNORMAL_THRESHOLD  # same event resumes
    )
    times = [i * 0.5 for i in range(len(labels))]  # whole sequence spans a few seconds
    assert times[-1] < REFLAG_COOLDOWN_SECONDS, "test setup must stay inside the cooldown window"

    results = _feed(labels, state, times)
    assert sum(results) == 1, f"expected exactly one notification (noise must not split the event), got {sum(results)}"


def test_genuinely_new_event_after_cooldown_fires_again():
    # First event fires. Long gap (news event, well past REFLAG_COOLDOWN_SECONDS)
    # of normal frames, then a second, unrelated event — must fire independently,
    # not be silently suppressed by the first alert.
    state = AlertState()
    first_event = [True] * CONSECUTIVE_ABNORMAL_THRESHOLD
    gap = [False] * 5
    second_event = [True] * CONSECUTIVE_ABNORMAL_THRESHOLD
    labels = first_event + gap + second_event

    times = []
    t = 0.0
    for _ in first_event:
        times.append(t)
        t += 0.5
    # Jump the clock well past the cooldown before the gap/second event.
    t += REFLAG_COOLDOWN_SECONDS + 5.0
    for _ in gap + second_event:
        times.append(t)
        t += 0.5

    results = _feed(labels, state, times)
    assert sum(results) == 2, (
        f"expected two independent notifications (one per real event), got {sum(results)}"
    )


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
