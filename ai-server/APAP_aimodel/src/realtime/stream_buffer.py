from __future__ import annotations

from collections import deque
from typing import Deque, Generic, Iterable, TypeVar


T = TypeVar("T")


class FrameBuffer(Generic[T]):
    """Fixed-size sliding window buffer for frames or pose landmarks."""

    def __init__(self, max_size: int) -> None:
        if max_size <= 0:
            raise ValueError("max_size must be greater than 0.")

        self.max_size = max_size
        self._items: Deque[T] = deque(maxlen=max_size)

    def add_frame(self, frame: T) -> None:
        self._items.append(frame)

    def add(self, item: T) -> None:
        self.add_frame(item)

    def extend(self, items: Iterable[T]) -> None:
        for item in items:
            self.add_frame(item)

    def is_ready(self) -> bool:
        return len(self._items) >= self.max_size

    def get_window(self) -> list[T]:
        return list(self._items)

    def clear(self) -> None:
        self._items.clear()

    def __len__(self) -> int:
        return len(self._items)

    @property
    def current_size(self) -> int:
        return len(self._items)
