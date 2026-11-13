from __future__ import annotations

import numpy as np

try:
    import mediapipe as mp
except ImportError as exc:  # pragma: no cover - depends on local environment
    mp = None
    _MEDIAPIPE_IMPORT_ERROR = exc
else:
    _MEDIAPIPE_IMPORT_ERROR = None


# Starting std for coordinate jitter, in MediaPipe's normalized [0,1] units.
# Kept as a module constant so it's easy to tune later.
GAUSSIAN_NOISE_STD = 0.01

_swap_map_cache: dict[int, int] | None = None


def _build_left_right_swap_map() -> dict[int, int]:
    """Map each MediaPipe Pose landmark index to its left/right counterpart.

    Built from the PoseLandmark enum (not a hardcoded index list) so it stays
    correct if MediaPipe ever reorders landmarks. Landmarks without a LEFT_/
    RIGHT_ prefix map to themselves.

    Known quirk: MOUTH_LEFT/MOUTH_RIGHT carry the L/R marker as a suffix, not a
    prefix, so this prefix-based match self-maps them instead of swapping them.
    This has no effect on make_feature_vector's output: features.py only reads
    the prefix-matched joints (indices 11-28, e.g. LEFT_SHOULDER/RIGHT_SHOULDER)
    for distances/angles, and its coordinate summary is a landmark-order-
    independent mean/std over all 33 points.
    """
    if mp is None:
        raise RuntimeError(
            "Missing required package: mediapipe. "
            "Install dependencies with `pip install -r requirements.txt`."
        ) from _MEDIAPIPE_IMPORT_ERROR

    by_name = {lm.name: lm.value for lm in mp.solutions.pose.PoseLandmark}
    swap_map: dict[int, int] = {}

    for name, index in by_name.items():
        if index in swap_map:
            continue  # already assigned when its counterpart was processed

        if name.startswith("LEFT_"):
            counterpart_name = "RIGHT_" + name[len("LEFT_"):]
        elif name.startswith("RIGHT_"):
            counterpart_name = "LEFT_" + name[len("RIGHT_"):]
        else:
            swap_map[index] = index  # central landmark (e.g. NOSE)
            continue

        counterpart_index = by_name.get(counterpart_name)
        if counterpart_index is None:
            raise ValueError(f"No {counterpart_name} counterpart found for {name}.")
        swap_map[index] = counterpart_index
        swap_map[counterpart_index] = index

    return swap_map


def _get_swap_map() -> dict[int, int]:
    global _swap_map_cache
    if _swap_map_cache is None:
        _swap_map_cache = _build_left_right_swap_map()
    return _swap_map_cache


def mirror_flip(window: np.ndarray) -> np.ndarray:
    """Horizontally mirror a (window_frames, 33, 4) pose window.

    Flips the x coordinate (channel 0) as 1 - x (MediaPipe's normalized [0,1]
    coordinates), leaves y, z, visibility untouched, and swaps left/right
    landmark indices so e.g. the flipped left-shoulder data ends up in the
    right-shoulder slot. Applying this twice returns the original window
    (see test_augmentation.py's idempotence check).
    """
    array = np.asarray(window, dtype=np.float32)
    if array.ndim != 3 or array.shape[-1] != 4:
        raise ValueError(
            "window must have shape (window_frames, num_landmarks, 4). "
            f"Got {array.shape}."
        )

    swap_map = _get_swap_map()
    num_landmarks = array.shape[1]
    perm = np.array(
        [swap_map.get(i, i) for i in range(num_landmarks)],
        dtype=np.intp,
    )

    flipped = array.copy()
    flipped[:, :, 0] = 1.0 - flipped[:, :, 0]
    return flipped[:, perm, :].astype(np.float32)


def add_gaussian_noise(
    window: np.ndarray,
    std: float,
    rng: np.random.Generator,
) -> np.ndarray:
    """Add coordinate jitter to a (window_frames, 33, 4) pose window.

    Noise is added to x, y, z (channels 0-2) only; visibility (channel 3) is
    left untouched. x and y are clipped back to [0, 1] (MediaPipe's normalized
    coordinate convention); z is left unclipped since depth is a relative value
    with no fixed range.
    """
    array = np.asarray(window, dtype=np.float32)
    if array.ndim != 3 or array.shape[-1] != 4:
        raise ValueError(
            "window must have shape (window_frames, num_landmarks, 4). "
            f"Got {array.shape}."
        )

    noisy = array.copy()
    noise = rng.normal(loc=0.0, scale=std, size=array[:, :, :3].shape)
    noisy[:, :, :3] = array[:, :, :3] + noise.astype(np.float32)
    noisy[:, :, 0] = np.clip(noisy[:, :, 0], 0.0, 1.0)
    noisy[:, :, 1] = np.clip(noisy[:, :, 1], 0.0, 1.0)
    return noisy.astype(np.float32)
