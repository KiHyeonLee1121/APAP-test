# APAP API 명세 (Backend)

비정상 행동 알람 플랫폼 백엔드 REST API 명세입니다. 구현 기준(Spring Boot 3.3 / Java 17 / MySQL 8)으로 작성되었습니다.

- Base URL(로컬): `http://localhost:8080`
- 모든 응답은 JSON. 인증은 **JWT Bearer 토큰**.

## 공통 응답 형식

성공:

```json
{ "success": true, "data": { }, "message": "선택적 메시지" }
```

실패:

```json
{ "success": false, "error": { "code": "ERROR_CODE", "message": "사람이 읽을 메시지" } }
```

### 에러 코드

| HTTP | code | 의미 |
|---|---|---|
| 400 | `BAD_REQUEST` | 잘못된 요청/도메인 규칙 위반 |
| 400 | `VALIDATION_ERROR` | 입력 검증 실패 |
| 401 | `UNAUTHORIZED` | 토큰 없음/만료/위조 |
| 403 | `FORBIDDEN` | 권한 부족 |
| 404 | `NOT_FOUND` | 리소스 없음 |
| 500 | `SERVER_ERROR` | 서버 내부 오류 |

## 인증 / 권한

- 로그인: 구글 OIDC ID 토큰을 검증한 뒤 **자체 JWT(HS256)** 를 발급한다.
- 보호된 API 호출 시 헤더: `Authorization: Bearer <accessToken>`
- 공개 경로(토큰 불필요): `GET /api/health`, `POST /api/auth/google`, `POST /api/analysis/callback`, Swagger(`/swagger-ui/**`, `/v3/api-docs/**`), 정적 페이지(`/`, `/google-login.html`)
- 역할: `ADMIN`, `MANAGER`, `VIEWER` (신규 가입 기본값 `MANAGER`)

| 작업 | 필요 권한 |
|---|---|
| 조회(GET): 영상/이벤트/분석작업/알림/대시보드/내 정보/케이스/로그인 이력 | 인증된 모든 역할(VIEWER 이상) |
| 쓰기: 영상 등록·업로드·수정·삭제, 분석 요청, 알림 읽음·테스트, 케이스 등록 | MANAGER 이상 |
| `POST /api/analysis/callback` | 공개(엣지/AI용, 추후 API Key 보호 권장) |

> 목록/요약/알림 등은 **쿼리 파라미터 userId를 받지 않으며**, JWT 토큰의 사용자 기준으로 동작한다.

---

## Auth

### POST /api/auth/google — 구글 로그인 (공개)

요청:
```json
{ "idToken": "<구글 OIDC ID 토큰(JWT)>" }
```
응답 `data`:
```json
{
  "accessToken": "<서비스 JWT>",
  "user": { "id": 1, "email": "user@example.com", "name": "홍길동", "pictureUrl": "https://...", "role": "MANAGER" }
}
```
동작: ID 토큰 검증(서명·만료·issuer·audience=`GOOGLE_CLIENT_ID`·`email_verified`) → `google_sub`/`email`로 find-or-create → JWT 발급. **성공 시 로그인 이력 1건 자동 기록**(7월 회의).

### GET /api/auth/me — 현재 사용자 (인증)
응답 `data`: 위 `user`와 동일 구조.

### GET /api/auth/login-history — 내 로그인 이력 (인증)
본인 이력만 최신순 조회. 응답 `data`:
```json
[ { "id": 3, "userId": 1, "loggedInAt": "2026-07-30T10:12:00" } ]
```

### POST /api/auth/logout — 로그아웃 (인증)
무상태. `data: null`, 클라이언트가 토큰 폐기.

---

## Case (감지 케이스, 7월 회의 신규)

서비스 시나리오(한강 교각 위험 행동 / 식당 식권 미제출 / 영화관 무단입장 / 마트 주머니 은닉)를 케이스로 관리한다. 초기 4종은 서버 기동 시 자동 시드된다.

### GET /api/cases — 케이스 목록 (인증)
```json
[ { "id": 1, "name": "한강 교각 위험 행동", "description": "...", "outMsg": "한강 교각 주변에서 위험 행동이 감지되었습니다..." } ]
```

### POST /api/user-cases — 유저 케이스 등록 (MANAGER+)
요청 `{ "caseId": 1 }` → 응답 `data`에 케이스 `outMsg` 포함(회의: in user_id, case_id → out msg). 중복 등록 시 400.
```json
{ "id": 1, "userId": 1, "caseId": 1, "caseName": "한강 교각 위험 행동", "outMsg": "...", "active": true }
```

### GET /api/user-cases — 내 케이스 목록 (인증)
위 UserCaseResponse 배열.

> **알림 연계**: 비정상 이벤트로 Alert가 생성될 때 유저에게 활성 케이스가 있으면 알림 메시지로 해당 케이스의 `outMsg`를 사용한다(여러 개면 가장 먼저 등록한 활성 케이스 기준, 없으면 기본 메시지).

---

## Video (`/api/videos`)

VideoResponse:
```json
{ "id": 1, "userId": 1, "type": "UPLOAD", "name": "샘플", "sourceUrl": "uploads/x.mp4", "status": "READY" }
```
- `type`: `UPLOAD` | `CCTV` | `EDGE_GATEWAY`
- `status`: `READY` | `ANALYZING` | `ERROR` | `DISABLED`

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| POST | `/api/videos` | MANAGER+ | 영상 소스 등록. body `{type, name, sourceUrl}` |
| POST | `/api/videos/upload` | MANAGER+ | 파일 업로드(multipart `file`) → 저장 후 VideoSource 생성 |
| GET | `/api/videos` | 인증 | 내 영상 목록 |
| GET | `/api/videos/{videoId}` | 인증(소유자) | 상세 |
| PATCH | `/api/videos/{videoId}` | MANAGER+(소유자) | 수정. body `{type, name, sourceUrl, status}` |
| DELETE | `/api/videos/{videoId}` | MANAGER+(소유자) | soft delete |

> 타인 소유 리소스 접근 시 정보 노출 방지를 위해 `404 NOT_FOUND` 반환.

---

## Analysis (`/api/analysis`)

AnalysisJobResponse:
```json
{ "id": 1, "videoSourceId": 1, "status": "DONE", "requestedAt": "...", "completedAt": "...", "errorMessage": null }
```
- `status`: `PENDING` | `RUNNING` | `DONE` | `FAILED`

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| POST | `/api/analysis/jobs` | MANAGER+ | 분석 요청. body `{videoSourceId}`. AI 서버 `/predict/video` **동기 호출** 후 DetectionEvent 저장, ABNORMAL이면 Alert 생성 |
| GET | `/api/analysis/jobs` | 인증 | 내 분석 작업 목록 |
| GET | `/api/analysis/jobs/{jobId}` | 인증 | 분석 작업 상세 |
| POST | `/api/analysis/callback` | 공개 | 엣지/AI가 결과 직접 전송. body 아래 |

콜백 요청:
```json
{
  "jobId": 1,
  "status": "DONE",
  "errorMessage": null,
  "events": [
    {
      "eventType": "ABNORMAL",
      "severity": "HIGH",
      "confidenceScore": 0.91,
      "detectedAt": "2026-07-01T12:10:00",
      "snapshotUrl": "uploads/snapshot-1.jpg",
      "clipUrl": "uploads/clip-1.mp4",
      "resultJson": "{\"reason\": \"...\"}"
    }
  ]
}
```
- `eventType`: `NORMAL` | `ABNORMAL` | `FALL` | `INTRUSION` | `ANOMALOUS` | `UNKNOWN`
- `severity`: `LOW` | `MEDIUM` | `HIGH` | `CRITICAL`
- `ABNORMAL/FALL/INTRUSION/ANOMALOUS`는 Alert 자동 생성

---

## Event (`/api/events`)

EventResponse:
```json
{
  "id": 1, "analysisJobId": 1, "videoSourceId": 1,
  "eventType": "ABNORMAL", "severity": "HIGH", "confidenceScore": 0.91,
  "detectedAt": "...", "snapshotUrl": "...", "clipUrl": "...", "resultJson": "..."
}
```

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/events` | 인증 | 내 감지 이벤트 목록(detectedAt 내림차순) |
| GET | `/api/events/{eventId}` | 인증 | 이벤트 상세 |

---

## Dashboard (`/api/dashboard`)

| Method | Path | 권한 | 응답 `data` |
|---|---|---|---|
| GET | `/api/dashboard/summary` | 인증 | `{videos, analysisJobs, abnormalEvents, unreadAlerts}` (모두 정수) |
| GET | `/api/dashboard/timeline` | 인증 | `[{date:"2026-07-01", count:3}, ...]` 날짜별 이벤트 수 |
| GET | `/api/dashboard/severity` | 인증 | `[{severity:"LOW", count:0}, ...]` 심각도별 수(4단계 전부 포함) |

`abnormalEvents`는 `ABNORMAL/FALL/INTRUSION/ANOMALOUS` 합계.

---

## Alert (`/api/alerts`)

AlertResponse:
```json
{
  "id": 1, "detectionEventId": 10, "receiverId": 1,
  "channel": "DASHBOARD", "status": "SENT", "message": "...",
  "sentAt": "...", "readAt": null
}
```
- `status`: `PENDING` | `SENT` | `FAILED` | `READ`
- `detectionEventId`는 테스트/시스템 알림의 경우 `null`

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/alerts` | 인증 | 내 알림 목록 |
| PATCH | `/api/alerts/{alertId}/read` | MANAGER+(수신자) | 읽음 처리 |
| POST | `/api/alerts/test` | MANAGER+ | 본인에게 테스트 알림 1건 생성 |

---

## Health

| Method | Path | 권한 | 응답 |
|---|---|---|---|
| GET | `/api/health` | 공개 | `{ "status": "ok" }` |

---

## AI 서버 연동 계약

> **2026-07 확인**: AI팀의 Anomaly Detection(정상만 학습→이상치 탐지) 반영 후에도 `/predict/video` 응답 스키마는 `{prediction, confidence, source, status(success/error), message}`로 **변경 없음**을 ai-server 코드로 확인함. 백엔드 매핑 그대로 유효.

백엔드는 AI 모델을 직접 실행하지 않고 HTTP로 연동한다.

- **백엔드 → AI**: `POST {AI_SERVER_URL}/predict/video`
  ```json
  { "video_path": "uploads/sample.mp4" }
  ```
- **AI → 백엔드(응답)**:
  ```json
  { "prediction": "normal|abnormal", "confidence": 0.87, "source": "...", "status": "success", "message": null }
  ```
- 변환: `abnormal → ABNORMAL`(그 외 `NORMAL`), confidence → severity(`>=0.9` CRITICAL / `>=0.75` HIGH / `>=0.5` MEDIUM / else LOW), `status!=success`면 job `FAILED`.
- ⚠️ `video_path`는 현재 백엔드 파일시스템 경로다. **AI 서버가 동일 경로(공유 볼륨 등)로 접근 가능해야 한다** — 배포 시 공유 스토리지/URL 방식 합의 필요.

## 환경변수

| 변수 | 설명 | 기본값 |
|---|---|---|
| `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` | MySQL 연결 | localhost/3306/abnormal_alarm/root/APAP2026 |
| `AI_SERVER_URL` | AI 추론 서버 | `http://localhost:8000` |
| `BASE_URL` | 백엔드 주소 | `http://localhost:8080` |
| `GOOGLE_CLIENT_ID` | 구글 OAuth 클라이언트 ID | — |
| `JWT_SECRET` | JWT 서명 키(32바이트+) | (미설정 시 임시키) |
| `JWT_EXPIRATION_MS` | 토큰 만료(ms) | `86400000` |

> 변경 이력: 이메일/비밀번호 인증 제거 → 구글 OIDC + JWT 도입, 시나리오 도메인 제거, soft delete 도입.
