"""Tests for src/api/main.py's _notify_backend_alert: the best-effort call that
tells the backend a real live-detection event happened, so it shows up in the
alert history page. No real backend is available in tests, so urllib is mocked.

Runnable standalone (no pytest) or via pytest.
"""
from __future__ import annotations

import os
import sys
from contextlib import contextmanager
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from src.api import main as api_main


@contextmanager
def _env(**overrides):
    original = {k: os.environ.get(k) for k in overrides}
    os.environ.update(overrides)
    try:
        yield
    finally:
        for k, v in original.items():
            if v is None:
                os.environ.pop(k, None)
            else:
                os.environ[k] = v


def test_no_backend_url_skips_request():
    with _env(BACKEND_URL=""):
        os.environ.pop("BACKEND_URL", None)
        with patch.object(api_main.urllib.request, "urlopen") as mock_urlopen:
            api_main._notify_backend_alert(42)
            mock_urlopen.assert_not_called()


def test_posts_video_source_id_to_alerts_live_endpoint():
    with _env(BACKEND_URL="http://backend:8080"):
        with patch.object(api_main.urllib.request, "urlopen") as mock_urlopen:
            api_main._notify_backend_alert(42)

            mock_urlopen.assert_called_once()
            (request,), kwargs = mock_urlopen.call_args
            assert request.full_url == "http://backend:8080/api/alerts/live"
            assert request.get_method() == "POST"
            assert b'"videoSourceId": 42' in request.data
            assert kwargs["timeout"] == 3


def test_trailing_slash_in_backend_url_does_not_double_up():
    with _env(BACKEND_URL="http://backend:8080/"):
        with patch.object(api_main.urllib.request, "urlopen") as mock_urlopen:
            api_main._notify_backend_alert(1)
            (request,), _ = mock_urlopen.call_args
            assert request.full_url == "http://backend:8080/api/alerts/live"


def test_network_failure_is_swallowed_not_raised():
    with _env(BACKEND_URL="http://backend:8080"):
        with patch.object(
            api_main.urllib.request,
            "urlopen",
            side_effect=api_main.urllib.error.URLError("connection refused"),
        ):
            api_main._notify_backend_alert(1)  # must not raise


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
