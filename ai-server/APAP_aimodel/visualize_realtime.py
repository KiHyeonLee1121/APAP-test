# visualize_realtime.py
import sys
import cv2
sys.path.insert(0, "src")

from src.stream.video_source import VideoSource
from src.realtime.realtime_processor import RealtimePoseProcessor
from src.utils import MODEL_PATH

source = 1

video_source = VideoSource(source)
video_source.open()

with RealtimePoseProcessor(
    model_path=MODEL_PATH,
    window_size=30,
    frame_skip=1,
    prediction_interval=10,
) as processor:

    label = "warming_up"
    confidence = 0.0
    color = (200, 200, 200)

    while True:
        success, frame = video_source.read()
        if not success:
            break

        result = processor.process_frame(frame)

        if result:
            status = result["status"]
            if status == "predicted":
                label = result["prediction"]
                confidence = result["confidence"]
                color = (0, 0, 255) if label == "abnormal" else (0, 255, 0)
            elif status == "no_pose":
                label = "no_pose"
                color = (200, 200, 200)

        cv2.putText(frame, f"{label} ({confidence:.2f})",
                    (20, 50), cv2.FONT_HERSHEY_SIMPLEX,
                    1.2, color, 2)

        cv2.rectangle(frame, (10, 10), (400, 70), color, 2)

        cv2.imshow("APAP - Realtime Analysis", frame)

        if cv2.waitKey(1) & 0xFF == ord("q"):
            break

video_source.release()
cv2.destroyAllWindows()