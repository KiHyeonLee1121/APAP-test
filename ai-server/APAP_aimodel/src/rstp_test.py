# rtsp_test.py
import cv2

url = "rtsp://YOUR_USERNAME:YOUR_PASSWORD@YOUR_CAMERA_IP:554/stream2"
cap = cv2.VideoCapture(url)
print("열림:", cap.isOpened())

if cap.isOpened():
    ret, frame = cap.read()
    print("프레임 읽기:", ret, frame.shape if ret else None)
else:
    print("연결 실패 — IP/계정/네트워크 확인 필요")

cap.release()