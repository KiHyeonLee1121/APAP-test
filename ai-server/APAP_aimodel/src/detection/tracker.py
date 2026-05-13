from __future__ import annotations


class ObjectTracker:
    """Object tracking interface.

    Current MVP behavior is a dummy tracker that returns detections unchanged.
    TODO: connect Ultralytics track mode, ByteTrack, or BoT-SORT behind this
    interface when the project moves beyond the central-server MVP.
    """

    def __init__(self, tracker_name: str = "dummy") -> None:
        self.tracker_name = tracker_name

    def update(self, detections: list[dict], frame=None) -> list[dict]:
        tracked: list[dict] = []
        for index, detection in enumerate(detections):
            item = dict(detection)
            item.setdefault("track_id", None)
            item.setdefault("tracker", self.tracker_name)
            item.setdefault("detection_index", index)
            tracked.append(item)
        return tracked
