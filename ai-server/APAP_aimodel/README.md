# APAP AI Model MVP

APAP(Abnormal Pattern Alarmer Platform)의 첫 번째 AI 모델 MVP입니다.

이 모델은 APAP 플랫폼에서 `영상 입력 -> 관절 좌표 추출 -> 간단한 feature 생성 -> normal/abnormal 분류` 흐름을 검증하기 위한 최소 구현입니다. 현재 버전은 MediaPipe Pose 기반 feature와 scikit-learn의 RandomForestClassifier를 사용합니다.

또한 사용자가 입력한 행동 설명을 바탕으로 synthetic data generation을 준비하는 초기 파이프라인을 포함합니다. 현재는 실제 비디오 생성 모델을 호출하지 않고, 시나리오 JSON, video generation prompt, metadata, 예상 synthetic video 저장 경로를 생성하는 MVP 단계입니다.

운영 방향은 일반 CCTV/IP Camera 영상을 중앙 서버에서 받아 추론하는 구조입니다. 현재 단계에서는 Jetson Nano, DeepStream, TensorRT 같은 엣지/고성능 최적화 도구를 붙이지 않고, OpenCV 기반 입력 처리, optional YOLO 객체 탐지, sliding window realtime inference, FastAPI inference server skeleton을 우선 제공합니다.

## 디렉토리 구조

```text
APAP_aimodel/
  README.md
  requirements.txt
  .gitignore

  data/
    raw/
      normal/
      abnormal/
    synthetic/
      videos/
        normal/
        abnormal/
      metadata/
    processed/

  src/
    __init__.py
    extract_pose.py
    features.py
    dataset.py
    model.py
    train.py
    infer.py
    utils.py
    synthetic/
      __init__.py
      scenario_parser.py
      prompt_builder.py
      video_generator.py
      synthetic_pipeline.py
    realtime/
      __init__.py
      stream_buffer.py
      realtime_processor.py
      realtime_infer.py
    stream/
      __init__.py
      rtsp_reader.py
      video_source.py
    detection/
      __init__.py
      yolo_detector.py
      tracker.py
    api/
      __init__.py
      main.py
      schemas.py

  docker/
    Dockerfile
    docker-compose.yml

  checkpoints/
    .gitkeep

  outputs/
    .gitkeep
```

## 설치 방법

프로젝트 루트에서 다음 명령을 실행합니다.

```bash
pip install -r APAP_aimodel/requirements.txt
```

## Real 데이터 위치

학습용 real CCTV mp4 파일을 아래 위치에 넣습니다.

```text
APAP_aimodel/data/raw/normal/
APAP_aimodel/data/raw/abnormal/
```

- 정상 행동 영상: `data/raw/normal/`
- 비정상 행동 영상: `data/raw/abnormal/`
- `normal`은 label `0`, `abnormal`은 label `1`로 처리됩니다.

## Synthetic 데이터 준비

사용자 행동 설명으로 synthetic video generation용 scenario metadata와 prompt를 생성할 수 있습니다.

```bash
cd APAP_aimodel
python -m src.synthetic.synthetic_pipeline --text "주머니에 손을 넣고 주변을 두리번거리는 행동" --label abnormal
```

실행 결과로 `data/synthetic/metadata/` 아래에 `scenario_날짜시간.json` 형식의 metadata 파일이 저장됩니다. JSON에는 다음 정보가 포함됩니다.

- `scenario_id`
- `label`
- `user_text`
- `parsed_scenario`
- `generated_prompt`
- `expected_video_path`
- `source: synthetic`
- `status: prompt_generated`

현재 버전의 `video_generator.py`는 mock generator입니다. 실제 mp4 파일을 만들지 않고, 나중에 생성된 synthetic video가 저장될 예상 경로만 준비합니다.

추후 Stable Video Diffusion, Runway, OpenAI video generation API, 자체 video diffusion 모델 등을 연결할 때는 `src/synthetic/video_generator.py`의 `BaseVideoGenerator` 인터페이스를 구현하면 됩니다.

## Synthetic 영상 위치

나중에 실제 synthetic mp4가 생성되면 아래 위치에 저장합니다.

```text
APAP_aimodel/data/synthetic/videos/normal/
APAP_aimodel/data/synthetic/videos/abnormal/
```

`dataset.py`는 real 데이터와 synthetic video 데이터를 함께 읽습니다. 따라서 synthetic 영상도 기존 real CCTV 영상과 동일하게 `extract_pose_landmarks -> make_feature_vector -> classifier train` 파이프라인을 탑니다. synthetic 영상이 아직 없으면 경고만 출력하고, 사용 가능한 real 데이터로 학습을 계속합니다.

## 학습 실행

```bash
cd APAP_aimodel
python -m src.train
```

학습이 완료되면 모델은 다음 위치에 저장됩니다.

```text
checkpoints/model_v1.pkl
```

안정적인 train/test split을 위해 각 클래스에 최소 2개 이상의 포즈가 감지되는 mp4 파일을 넣는 것을 권장합니다. 일부 영상에서 사람이 감지되지 않거나 처리에 실패하면 해당 파일만 skip합니다.

## 추론 실행

Batch inference는 mp4 파일 하나 전체를 분석해서 한 번의 normal/abnormal 결과를 출력합니다.

```bash
cd APAP_aimodel
python -m src.infer --video data/raw/normal/sample.mp4
```

출력 예시:

```text
Prediction: normal
Confidence: 0.87
```

모델 파일이 없으면 먼저 `python -m src.train`을 실행하라는 안내가 출력됩니다.

## 실시간 추론 구조

Realtime inference는 운영 환경에서 CCTV, IP Camera, webcam, 또는 백엔드 서버가 전달하는 frame stream을 처리하기 위한 구조입니다. 매 프레임마다 최종 판단을 내리는 대신, 최근 N개의 pose landmark를 sliding window buffer에 쌓고 일정 간격마다 예측을 갱신합니다.

실시간 처리 흐름은 다음을 기준으로 설계되어 있습니다.

```text
CCTV / IP Camera
-> Backend Server receives stream
-> resize / scaling / preprocessing
-> AI model process receives frames
-> MediaPipe Pose landmark extraction
-> sliding window buffer
-> feature vector generation
-> normal/abnormal prediction
-> result returned to backend
-> dashboard/log/alert
```

테스트 실행 예시:

```bash
cd APAP_aimodel
python -m src.realtime.realtime_infer --source data/raw/normal/sample.mp4
python -m src.realtime.realtime_infer --source 0
python -m src.realtime.realtime_infer --source rtsp://YOUR_CAMERA_URL
```

`--source 0`은 기본 웹캠 입력을 의미합니다. `--source rtsp://...` 형식으로 일반 CCTV/IP Camera RTSP 스트림도 OpenCV VideoCapture 기반으로 열 수 있습니다.

주요 옵션:

- `--window-size 30`: 최근 30개의 pose landmark 기준으로 판단
- `--frame-skip 3`: 3프레임마다 한 번 pose 추출 수행
- `--prediction-interval 10`: buffer가 준비된 뒤 10개 처리 프레임마다 예측
- `--print-status`: `warming_up`, `no_pose` 상태도 함께 출력
- `--use-object-detection`: optional YOLO 객체 탐지 활성화
- `--use-tracking`: detection 결과에 dummy tracker 인터페이스 적용
- `--use-object-features-for-prediction`: 객체 feature를 pose feature와 결합해서 예측. 이 옵션은 결합 feature로 학습한 모델이 있을 때만 사용해야 합니다.

백엔드 서버 연동 시에는 CLI 대신 `src.realtime.realtime_processor.RealtimePoseProcessor`를 import해서 사용할 수 있습니다. 서버가 resize/scaling/preprocessing된 `np.ndarray` frame을 넘기면, `process_frame(frame)`이 내부 pose buffer 상태를 유지하며 예측 가능한 시점에 다음 형태의 dict를 반환합니다. 입력 frame은 기본적으로 OpenCV의 BGR 포맷으로 처리하며, 서버가 RGB frame을 넘기는 경우 `input_color_format="RGB"` 옵션을 사용할 수 있습니다.

```python
{
    "prediction": "abnormal",
    "confidence": 0.82,
    "window_size": 30,
    "status": "predicted",
}
```

Batch inference와 realtime inference는 모두 같은 `checkpoints/model_v1.pkl` 모델 파일을 사용하고, 같은 `make_feature_vector()` feature 생성 로직을 재사용합니다. synthetic data generation 파이프라인은 학습 데이터 보강용이고, realtime inference 파이프라인은 실제 운영 추론용으로 분리되어 있습니다.

## 중앙 서버 CCTV 입력 구조

현재 지원하는 입력은 다음과 같습니다.

- mp4 파일: `data/raw/normal/sample.mp4`
- 웹캠: `0`
- RTSP CCTV/IP Camera: `rtsp://YOUR_CAMERA_URL`

입력 처리는 `src.stream.video_source.VideoSource`에서 공통 인터페이스로 다룹니다. RTSP 전용 helper는 `src.stream.rtsp_reader.RTSPReader`이며, 현재는 OpenCV VideoCapture 기반의 단순 MVP입니다. 추후 reconnect backoff, buffering, async reader를 이 인터페이스 뒤에 붙일 수 있습니다.

## 객체 탐지와 추적

`src.detection.yolo_detector.YOLODetector`는 Ultralytics YOLO 기반 객체 탐지 인터페이스입니다. 기본 모델은 가벼운 `yolov8n.pt`이며, `ultralytics`가 설치되지 않았거나 모델 로드가 실패하면 명확한 에러 메시지를 냅니다. import 시점에 YOLO를 바로 로드하지 않고, 실제 `detect(frame)` 호출 시 lazy load합니다.

탐지 결과 예시:

```python
[
    {
        "class_id": 0,
        "class_name": "person",
        "confidence": 0.91,
        "bbox": [x1, y1, x2, y2],
    }
]
```

`src.detection.tracker.ObjectTracker`는 현재 detection 결과를 그대로 반환하는 dummy tracker입니다. ByteTrack, BoT-SORT, Ultralytics track mode는 아직 연결하지 않았고, 나중에 이 인터페이스 뒤에 붙이는 구조입니다.

객체 feature는 `features.py`의 `make_object_features()`와 `combine_features()`로 준비되어 있습니다. 기존 train/infer는 pose feature만 사용하므로 기존 모델은 깨지지 않습니다.

## FastAPI 서버

중앙 서버에서 AI 모델을 별도 프로세스로 띄울 수 있도록 FastAPI skeleton을 제공합니다.

```bash
cd APAP_aimodel
uvicorn src.api.main:app --host 0.0.0.0 --port 8000
```

엔드포인트:

- `GET /health`: 서버 상태 확인
- `POST /predict/video`: `video_path`를 받아 기존 batch `predict_video()` 로직으로 예측

요청 예시:

```json
{
  "video_path": "data/raw/normal/sample.mp4"
}
```

응답 예시:

```json
{
  "prediction": "normal",
  "confidence": 0.87,
  "source": "data/raw/normal/sample.mp4",
  "status": "success",
  "message": null
}
```

현재 API는 파일 업로드, RTSP 등록, 장기 stream session 관리는 구현하지 않습니다. 이 부분은 백엔드 서버 연동 단계에서 확장할 예정입니다.

## Docker 개발 실행

Docker 초안은 개발용입니다.

```bash
cd APAP_aimodel
docker compose -f docker/docker-compose.yml up --build
```

컨테이너는 `8000:8000` 포트를 열고 `uvicorn src.api.main:app --host 0.0.0.0 --port 8000 --reload`로 실행됩니다.

## 현재 버전의 한계

- MediaPipe Pose로 감지된 관절 좌표만 사용합니다.
- 영상의 긴 시간 흐름을 깊게 학습하지 않고, 전체 프레임의 통계 feature를 사용합니다.
- 행동의 맥락, 표정, 객체, 주변 환경 정보는 아직 실제 모델 feature에 반영하지 않습니다.
- synthetic pipeline은 실제 비디오 생성이 아니라 prompt/metadata 생성 단계입니다.
- realtime pipeline은 서버 연동 인터페이스와 local stream 테스트 구조만 제공하며, 실제 CCTV 서버 통신은 아직 포함하지 않습니다.
- YOLO 객체 탐지와 dummy tracker는 optional 구조이며, 기존 pose-only 모델의 기본 feature 차원을 바꾸지 않습니다.
- 실제 ByteTrack 연결, Jetson/DeepStream/TensorRT 최적화, 대규모 행동 인식 모델은 아직 구현하지 않습니다.
- Stable Video Diffusion, ControlNet, Diffusers 기반 synthetic video 생성은 이번 단계에 포함하지 않습니다.
- 데이터가 적으면 RandomForestClassifier의 성능과 confidence가 안정적이지 않을 수 있습니다.
- 카메라 각도, 거리, 조명, 가려짐에 따라 pose landmark 품질이 달라질 수 있습니다.

## 추후 확장 방향

- 실제 video generation backend 연결
- 백엔드 서버와 realtime frame transport 연동
- RTSP reconnect, stream buffering, async reader 강화
- ByteTrack 또는 BoT-SORT 실제 연결
- YOLO detection 결과와 pose feature를 결합한 모델 학습
- 표정 분석 모듈 추가
- 객체 탐지 모듈 추가
- 손과 객체 간 거리, 사람과 난간/ATM/가방 간 관계 feature 추가
- LSTM 또는 Transformer 기반 시계열 행동 분석
- Anomaly Detection 기반 비정상 행동 탐지
- 실시간 CCTV 스트림 처리
- 백엔드 API 및 관리자 대시보드 연동
- feature 저장, 실험 관리, 모델 버전 관리 구조 추가
