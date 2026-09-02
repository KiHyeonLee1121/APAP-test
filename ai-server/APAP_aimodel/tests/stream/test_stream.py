# test_stream.py
import sys
import os
ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../"))
sys.path.insert(0, os.path.join(ROOT_DIR, "src"))
VIDEO_PATH = os.path.join(ROOT_DIR, "data/raw/normal/test_video.mp4")

# test_stream.py 상단에 임시로 추가
print(f"ROOT_DIR: {ROOT_DIR}")
print(f"VIDEO_PATH: {VIDEO_PATH}")
print(f"파일 존재 여부: {os.path.exists(VIDEO_PATH)}")

import time
from stream.video_source import VideoSource
from stream.rtsp_reader import RTSPReader

# ── 테스트 1: VideoSource로 파일 읽기 ──
def test_video_file(path: str):
    print(f"\n[TEST 1] VideoSource - 파일: {path}")
    with VideoSource(path) as vs:
        frame_count = 0
        start = time.time()

        while True:
            success, frame = vs.read()
            if not success:
                break
            frame_count += 1

        elapsed = time.time() - start
        print(f"  읽은 프레임: {frame_count}")
        print(f"  소요 시간:   {elapsed:.2f}초")
        print(f"  처리 FPS:    {frame_count / elapsed:.1f}")


# ── 테스트 2: 없는 파일 에러 처리 확인 ──
def test_missing_file():
    print("\n[TEST 2] 없는 파일 에러 처리")
    try:
        with VideoSource("not_exist.mp4") as vs:
            vs.read()
    except FileNotFoundError as e:
        print(f"  ✅ 정상 에러 발생: {e}")


# ── 테스트 3: frame_skip 성능 비교 ──
def test_frame_skip(path: str):
    print("\n[TEST 3] frame_skip 성능 비교")

    # VideoSource로 frame_skip 흉내내기
    for skip in [1, 2, 5]:
        with VideoSource(path) as vs:
            count = 0
            start = time.time()
            i = 0
            while True:
                success, frame = vs.read()
                if not success:
                    break
                i += 1
                if (i - 1) % skip == 0:
                    count += 1
            elapsed = time.time() - start
            print(f"  skip={skip}: 처리 프레임 {count}, {elapsed:.2f}초")


# ── 테스트 4: 프레임 실제로 보기 ──
def test_display(path: str):
    print("\n[TEST 4] 프레임 화면 출력 (q 누르면 종료)")
    import cv2
    with VideoSource(path) as vs:
        while True:
            success, frame = vs.read()
            if not success:
                break
            cv2.imshow("Test", frame)
            if cv2.waitKey(1) & 0xFF == ord("q"):
                break
    cv2.destroyAllWindows()


if __name__ == "__main__":

    test_video_file(VIDEO_PATH)
    test_missing_file()
    test_frame_skip(VIDEO_PATH)
    test_display(VIDEO_PATH)