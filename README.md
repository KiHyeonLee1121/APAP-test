# 비정상행동 알람 플랫폼

| 담당 | 작업 위치 | 설명 |
|---|---|---|
| 프론트엔드 담당 | frontend/ | 관리자 대시보드, 화면 구현 |
| 백엔드 담당 | backend/ | API 서버, DB 연동, 알람 이벤트 관리 |
| AI 담당 | ai-server/, ml/ | 이상행동 탐지 모델 추론 서버 및 모델 학습 |
| 문서 담당 | docs/ | 아키텍처, API 명세, 회의록, 보고서 관리 |
| 데이터 담당 | data/ | 데이터셋 설명 및 관리 방식 기록 |

## 프로젝트 개요

비정상행동 알람 플랫폼은 AI 기반 영상 분석을 통해 낙상, 침입, 이상행동 등 비정상행동을 탐지하고 관리자에게 알람을 제공하는 시스템입니다.

본 저장소는 한이음 드림업 프로젝트 팀원들이 함께 개발하기 위한 초기 협업 환경입니다. 현재 단계에서는 실제 프론트엔드, 백엔드, AI 프레임워크를 확정하지 않고, 향후 React, Spring Boot, FastAPI, Node.js, Python 기반 AI 서버 등으로 확장할 수 있는 구조를 준비합니다.

## 주요 기능

- 영상 기반 이상행동 탐지
- 이상행동 발생 시 알람 이벤트 생성
- 관리자 대시보드 제공
- 이벤트 로그 저장 및 조회
- AI 추론 서버와 백엔드 서버 연동

## 예상 시스템 구조

```text
Frontend -> Backend API -> AI Inference Server -> Database/Storage
```

## 폴더 구조

```text
.
├─ frontend/      # 프론트엔드 담당: 관리자 대시보드, 화면 구현
├─ backend/       # 백엔드 담당: API 서버, DB 연동, 알람 이벤트 관리
├─ ai-server/     # AI 담당: 이상행동 탐지 모델 추론 서버
├─ ml/            # AI 담당: 모델 학습, 실험, 추론 코드
├─ data/          # 데이터 담당: 데이터셋 설명 및 관리 방식 기록
├─ docs/          # 문서 담당: 아키텍처, API 명세, 회의록, 보고서
├─ .github/       # GitHub Actions, Issue/PR 템플릿
├─ README.md
├─ .env.example
├─ .gitignore
├─ AGENTS.md
└─ docker-compose.yml
```

## 로컬 실행 방법

추후 작성 예정입니다.

현재는 실제 실행 코드가 없으며, 각 애플리케이션의 기술스택이 확정된 뒤 실행 방법을 업데이트합니다.

## 브랜치 전략

- `main`: 발표/시연 가능한 안정 버전
- `develop`: 개발 통합 브랜치
- `feature/*`: 기능 개발 브랜치

## 협업 규칙

- 이슈를 생성한 뒤 작업합니다.
- 작업은 `feature/*` 또는 목적에 맞는 별도 브랜치에서 진행합니다.
- 작업 완료 후 Pull Request를 생성합니다.
- 팀원 리뷰 후 `develop` 브랜치에 merge합니다.
- `main` 브랜치에 직접 push하지 않습니다.

## 데이터/모델 관리 주의사항

- 원본 영상, 학습 데이터, 모델 파일은 GitHub에 올리지 않습니다.
- API Key, DB 비밀번호, JWT Secret 등 비밀값은 GitHub에 올리지 않습니다.
- `data/README.md`에는 데이터 출처와 관리 방법만 기록합니다.
- 모델 파일은 별도 스토리지 또는 릴리즈 정책을 정한 뒤 관리합니다.


## 개발 환경 설정

- 본 프로젝트는 Windows, macOS 환경에서 동일하게 실행될 수 있도록 아래 기준을 따른다.

### 기본 환경

- Python 3.10.x 사용
- VS Code 사용 권장
- Python 가상환경은 `venv` 사용
- 패키지는 `requirements.txt` 기준으로 설치
- GPU 연산은 로컬이 아닌 AWS EC2 GPU 서버에서 수행

### 코드 작성 규칙

- 파일명은 소문자와 언더스코어 사용
  - 예: `pose_estimator.py`, `rule_engine.py`
- 경로 처리는 `pathlib` 사용
  - OS별 경로 차이 방지 목적
- 줄바꿈 형식 통일을 위해 `.gitattributes` 사용

