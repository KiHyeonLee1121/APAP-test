# APAP Backend

APAP 프로젝트의 백엔드 API 서버입니다.

## 기술스택

- Java 17
- Spring Boot 3.3
- Spring Data JPA / Hibernate
- MySQL 8
- springdoc OpenAPI 2.5 (Swagger UI)
- Docker

## 실행 환경

- Java 17 이상
- MySQL 8 (로컬 또는 Docker)
- IntelliJ IDEA 또는 Maven CLI

## 로컬 실행 방법

### 1. 환경변수 설정

프로젝트 루트의 `.env.example`을 복사해 `.env`로 만들고 값을 채웁니다.

```powershell
cp .env.example .env
```

| 변수 | 설명 | 기본값 |
|---|---|---|
| `DB_HOST` | MySQL 호스트 | `localhost` |
| `DB_PORT` | MySQL 포트 | `3306` |
| `DB_NAME` | 데이터베이스 이름 | `abnormal_alarm` |
| `DB_USERNAME` | MySQL 사용자 | `root` |
| `DB_PASSWORD` | MySQL 비밀번호 | — |
| `AI_SERVER_URL` | AI 서버 주소 | `http://localhost:8000` |
| `BASE_URL` | 백엔드 서버 주소 | `http://localhost:8080` |
| `GOOGLE_CLIENT_ID` | 구글 OAuth 2.0 클라이언트 ID (구글 로그인용) | — |
| `JWT_SECRET` | JWT(HS256) 서명 비밀키. 운영은 32바이트 이상 필수 (미설정 시 부팅마다 임시 키 생성) | — |
| `JWT_EXPIRATION_MS` | 액세스 토큰 만료 시간(ms) | `86400000` (24h) |
| `APP_STORAGE_MODE` | 영상 저장 모드. `local`(uploads/ 디렉터리) 또는 `s3`(AWS S3) | `local` |
| `S3_BUCKET` | S3 버킷 이름 (s3 모드에서만 사용) | `project10-86-virg-apap-media` |
| `AWS_REGION` | S3 리전 (s3 모드, 계정 정책상 us-east-1 고정) | `us-east-1` |
| `ANALYSIS_AUTO_ON_UPLOAD` | 영상 업로드 시 자동 분석 여부 | `true` |
| `MAX_UPLOAD_SIZE` | 업로드 파일 1개 최대 크기 (`-1`은 무제한) | `-1` |
| `MAX_UPLOAD_REQUEST_SIZE` | 업로드 요청 전체 최대 크기 (`-1`은 무제한) | `-1` |

> **영상 용량 제한**: Spring 기본값은 파일 1MB·요청 10MB라 영상 업로드가 막힙니다. 기본 설정을 무제한(`-1`)으로 풀어두었고, 파일은 메모리에 담지 않고 임시 파일로 흘려보냅니다. 서버 디스크 용량이 걱정되면 `MAX_UPLOAD_SIZE=2GB`처럼 상한을 걸 수 있습니다.

> **S3 자격증명 관련**: 액세스 키/시크릿 환경변수는 사용하지 않습니다. 팀 AWS 계정은 액세스 키 발급이 불가능하며, EC2에 부착된 인스턴스 프로파일 Role로 자동 인증됩니다. 따라서 **로컬에서는 s3 모드가 동작하지 않고**, 기본값인 local 모드로 개발합니다.

### 2. MySQL 준비

Docker를 사용하는 경우 프로젝트 루트에서:

```powershell
docker-compose up -d
```

로컬 MySQL을 사용하는 경우 `abnormal_alarm` 데이터베이스를 직접 생성합니다.

```sql
CREATE DATABASE abnormal_alarm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

> **스키마 변경 주의**: `ddl-auto: update`는 기존 컬럼의 삭제/제약 변경을 자동 반영하지 않습니다. 이전 스키마로 만든 로컬 DB가 있다면 관련 테이블을 비우거나 데이터베이스를 재생성한 뒤 서버를 다시 띄우세요. 주요 변경:
> - `users`: 구글 로그인 도입으로 `password_hash` 제거, `google_sub`(NOT NULL, unique)·`picture_url` 추가
> - 전 엔티티에 soft delete용 `deleted` 컬럼 추가(영상은 삭제 시 `deleted=true`로만 처리)
> - `alerts.detection_event_id`가 nullable로 변경(테스트/시스템 알림 지원)
> - (7월 회의) 신규 테이블 `login_history`(로그인 이력), `detection_cases`·`user_cases`(감지 케이스 구독) — `ddl-auto: update`로 자동 생성되며, 케이스 4종은 서버 기동 시 자동 시드됨

### 3. 구글 OAuth 클라이언트 ID 준비

구글 로그인을 사용하려면 [Google Cloud Console](https://console.cloud.google.com/apis/credentials)에서 **OAuth 2.0 클라이언트 ID**를 발급받아 `.env`의 `GOOGLE_CLIENT_ID`에 넣습니다. (프론트엔드와 동일한 클라이언트 ID를 사용)

### 4. 서버 실행

**IntelliJ:**

1. `backend/` 폴더를 IntelliJ로 열기
2. `File` → `Project Structure` → SDK를 Java 17 이상으로 설정
3. `ApapBackendApplication.java`에서 실행 버튼 클릭

**Maven CLI:**

```powershell
cd backend
./mvnw spring-boot:run
```

정상 실행 시 콘솔에 다음이 표시됩니다.

```text
Tomcat started on port 8080
Started ApapBackendApplication
```

### 5. 동작 확인

```text
GET http://localhost:8080/api/health
```

응답: `{"success":true,"data":{"status":"ok"}}`

**Swagger UI:**

```text
http://localhost:8080/swagger-ui/index.html
```

## 구글 로그인 테스트 (실제 로그인 흐름)

로그인 테스트 페이지를 **백엔드 서버(8080)가 직접 서빙**합니다. 별도 정적 서버나 추가 포트가 필요 없고, 같은 출처라 CORS 설정도 필요 없습니다.

1. [Google Cloud Console](https://console.cloud.google.com/apis/credentials)에서 **OAuth 2.0 클라이언트 ID(웹 애플리케이션)**를 발급하고, **승인된 JavaScript 원본**에 `http://localhost:8080`을 추가합니다.
2. 발급한 클라이언트 ID를 환경변수 `GOOGLE_CLIENT_ID`로 주입하고 백엔드를 실행합니다. (IntelliJ Run Configuration의 Environment variables 또는 셸 `$env:GOOGLE_CLIENT_ID=...`)
3. 브라우저로 **`http://localhost:8080/google-login.html`** 접속 → 클라이언트 ID 입력 → **초기화** → **구글 로그인** 버튼으로 로그인.
4. 응답으로 내려온 `accessToken`(JWT)과 사용자 정보가 표시되고, `/api/auth/me` 호출 버튼으로 Bearer 인증을 확인할 수 있습니다.

> 페이지는 `src/main/resources/static/google-login.html`에 있으며 상대 경로(`/api/auth/google`)로 호출합니다.
> ID 토큰 없이 API만 빠르게 호출하려면 OAuth 2.0 Playground에서 "Use your own OAuth credentials"로 `id_token`을 발급받아 Swagger/Postman/`apap-api.http`에 붙여 넣어도 됩니다. (외부+테스트 상태면 로그인 계정을 **테스트 사용자**에 등록)

## Postman으로 테스트하기

`backend/postman/` 폴더의 두 파일을 Postman에 import합니다.

- `APAP.postman_collection.json`
- `APAP.local.postman_environment.json`

권장 실행 순서:

```text
0. 서버 상태 / 서버 상태 확인
1. Auth / 구글 로그인
2. Cameras / 카메라·영상 등록
3. Analysis / AI 분석 요청
4. Analysis / AI 결과 callback
5. Events / 감지 이벤트 목록 조회
6. Alerts / 알림 목록 조회
7. Dashboard / 대시보드 요약 조회
```

`구글 로그인`은 환경 변수 `googleIdToken`에 실제 구글 ID 토큰을 넣어야 동작하며, 응답의 `user.id`를 `userId`, `accessToken`(JWT)을 `accessToken` 변수로 자동 저장합니다. 컬렉션 레벨에 Bearer 인증(`{{accessToken}}`)이 설정되어 있어 로그인 이후 요청은 자동으로 토큰이 실립니다. (공개 요청인 서버 상태/구글 로그인/AI 콜백은 인증 없음으로 설정됨). `카메라·영상 등록`, `AI 분석 요청`도 응답 ID를 Postman 변수에 자동 저장합니다.

> 보호된 API는 `Authorization: Bearer <accessToken>` 헤더가 필요하고, 모든 조회는 토큰의 사용자 기준으로 동작합니다(쿼리 `userId` 없음). 권한: 조회는 VIEWER 이상, 쓰기(영상 등록/수정/삭제·분석 요청·알림)는 MANAGER 이상.

## 도메인 구조

| 패키지 | 역할 |
|---|---|
| `auth` | 구글 로그인(OIDC 검증), JWT 발급/검증, 인증 필터, 사용자 find-or-create |
| `user` | 사용자 계정, 권한(ADMIN/MANAGER/VIEWER) |
| `video` | 영상 소스(UPLOAD/CCTV/EDGE_GATEWAY) 관리, soft delete, 리셋 |
| `storage` | 업로드 저장소 추상화 — local(파일)/s3(AWS S3) 모드 분기, 키 형식 `videos/{uuid}-{filename}` |
| `analysis` | AI 서버 분석 요청, 콜백 수신, AnalysisJob 상태 관리 |
| `event` | AI 결과로 생성된 DetectionEvent 저장/조회 |
| `alert` | 알림 이력, 테스트 알림, 리셋 |
| `dashboard` | 통계/요약/타임라인/심각도 조회 API |
| `config` | Spring Security 설정, CORS, Swagger(OpenAPI) Bearer 스킴 |
| `common` | ApiResponse, ApiError, BaseEntity, GlobalExceptionHandler |

## AI 서버 연동

백엔드는 AI 모델을 직접 실행하지 않습니다. AI 서버(`ai-server/APAP_aimodel`)는 영상을 받아 normal/abnormal 이진 분류 결과(`prediction`, `confidence`)만 반환합니다.

기본 흐름(동기 호출):

1. `POST /api/analysis/jobs`(body: `videoSourceId`) → 백엔드가 AI 서버 `AI_SERVER_URL/predict/video`(body: `video_path`)를 동기 호출
2. 응답 예시:
   ```json
   { "prediction": "abnormal", "confidence": 0.91, "source": "...", "status": "success", "message": null }
   ```
3. 백엔드가 `prediction`을 DetectionEventType(NORMAL/ABNORMAL)으로, `confidence`를 Severity로 변환해 DetectionEvent 저장. 비정상 결과여도 Alert는 자동 생성하지 않음

보조 흐름(콜백): 엣지 디바이스(CCTV/카메라)가 직접 분석 결과를 보내는 경우 `POST /api/analysis/callback`으로 DetectionEvent 목록을 전송합니다. AI 모델 고도화 시 FALL/INTRUSION/ANOMALOUS 같은 이벤트 타입도 이 경로로 수용합니다.

### 업로드 시 자동 분석

**분석 버튼을 누르지 않아도, 영상을 업로드하면 분석이 자동으로 시작됩니다.**

- `POST /api/videos/upload` 요청 하나로 저장 → 분석 작업 생성 → 백그라운드 분석까지 이어집니다.
- AI 호출은 영상 길이만큼 시간이 걸리므로 **업로드 응답은 기다리지 않고 즉시 반환**됩니다. 응답에 담긴 `analysisJobId`로 `GET /api/analysis/jobs/{jobId}`를 조회하면 진행 상황(`PENDING` → `DONE`/`FAILED`)을 확인할 수 있습니다.
- 영상 상태는 분석 중 `ANALYZING`, 완료 시 `READY`, 실패 시 `ERROR`로 바뀝니다.
- 자동 분석은 **업로드(UPLOAD 타입)에만** 적용됩니다. `POST /api/videos`로 CCTV 주소만 등록하는 경우는 기존처럼 수동 요청이 필요합니다.
- 끄려면 `ANALYSIS_AUTO_ON_UPLOAD=false`로 실행하세요. 이 경우 기존처럼 `POST /api/analysis/jobs`로 직접 요청해야 합니다.
- `POST /api/analysis/jobs`는 **재분석** 용도로 계속 사용할 수 있습니다(동기 실행).

> `video_path`에는 `VideoSource.sourceUrl`이 그대로 전달됩니다. local 모드에선 파일 경로, s3 모드에선 S3 객체 키(`videos/{uuid}-{filename}`)이며, s3 모드에서는 AI 서버가 이 키로 버킷에서 영상을 직접 읽습니다.

## 저장된 영상 / 알림 리셋

사용자가 화면을 비울 수 있는 리셋 기능입니다. **데이터를 지우지 않고 화면에서만 감춥니다.**

| 기능 | API | 숨기는 대상 |
|---|---|---|
| 저장된 영상 리셋 | `POST /api/videos/reset` | 내 영상 + 그 영상의 분석 작업·감지 이벤트 |
| 알림 리셋 | `POST /api/alerts/reset` | 내 알림 전부 (읽음/안읽음 무관) |

- 두 리셋은 **서로 독립**입니다. 영상을 리셋해도 알림은 남고, 그 반대도 같습니다.
- 응답에 숨긴 건수(`hiddenCount`)가 담깁니다.
- **DB 행과 S3/로컬 파일은 지우지 않습니다.** `deleted` 값만 `true`로 바뀝니다.
- 본인 데이터만 대상이며, 다른 사용자 데이터는 영향을 받지 않습니다.

### 숨긴 데이터 되살리기

복구 API는 없습니다. 필요하면 DB에서 직접 되돌립니다(예: 특정 사용자의 영상 복구).

```sql
UPDATE video_sources SET deleted = false WHERE user_id = 1;
UPDATE analysis_jobs  SET deleted = false WHERE video_source_id IN (SELECT id FROM video_sources WHERE user_id = 1);
UPDATE detection_events SET deleted = false WHERE video_source_id IN (SELECT id FROM video_sources WHERE user_id = 1);
UPDATE alerts SET deleted = false WHERE receiver_id = 1;
```

## S3 저장소 전환 (운영 배포 시)

7월 회의 결정에 따라 운영에서는 영상을 S3에 저장합니다. 로컬에서는 검증이 불가능하므로(액세스 키 발급 불가 계정) EC2 배포 후 아래 체크리스트로 확인합니다.

**전환 방법**: 환경변수 `APP_STORAGE_MODE=s3` 설정 후 재기동 (버킷/리전 기본값: `project10-86-virg-apap-media` / `us-east-1`)

**EC2 배포 후 검증 체크리스트:**

1. ✅ EC2에 인스턴스 프로파일 Role(`SafeRole-project10-86-virg`)이 부착되어 있는지 확인
2. ✅ `APP_STORAGE_MODE=s3`로 백엔드 기동 → 에러 없이 시작되는지 확인
3. ✅ `POST /api/videos/upload`로 영상 업로드 → 응답 `sourceUrl`이 `videos/{uuid}-{filename}` 형식인지 확인
4. ✅ S3 버킷(us-east-1)에 해당 키로 객체가 생성되었는지 확인
5. ⬜ `POST /api/analysis/jobs`로 분석 요청 → AI 서버가 해당 키로 S3에서 영상을 읽어 응답하는지 확인 (**AI 서버 측에도 S3 읽기 권한 필요 — AI팀 준비 후 검증**)

> 2026-08-31 EC2(t3.small, us-east-1)에 배포해 1~4번 검증 완료. 업로드 API 응답 `sourceUrl`이 `videos/{uuid}-test-video.mp4`로 반환되고 S3에 실제 객체(2048 bytes, `video/mp4`)가 생성되는 것을 확인했습니다. 5번은 AI 서버 배포 후 진행합니다.

### 운영 서버 실행 방식 (EC2)

백엔드는 systemd 서비스로 등록되어 있어 인스턴스를 재부팅해도 자동 실행됩니다.

```bash
sudo systemctl status apap-backend     # 상태 확인
sudo systemctl restart apap-backend    # 재시작
sudo journalctl -u apap-backend -f     # 실시간 로그
```

- 환경변수 파일: `/etc/apap/backend.env` (600 권한, `JWT_SECRET` 등 비밀값 포함 — 깃에 올리지 않음)
- 실행 파일: `/home/ubuntu/apap-backend.jar`
- DB: EC2 로컬 MySQL, 애플리케이션 전용 계정 `apap` 사용(root 아님)
- **새 버전 배포**: 로컬에서 `./mvnw -DskipTests package` → jar를 서버로 전송 → `sudo systemctl restart apap-backend`

> 구글 로그인을 서버에서 쓰려면 `/etc/apap/backend.env`의 `GOOGLE_CLIENT_ID`를 채우고, 구글 콘솔의 **승인된 JavaScript 원본**에 서버 주소(`http://<퍼블릭IP>:8080`)를 추가한 뒤 서비스를 재시작해야 합니다.

**주의**: 라벨/케이스 등 분류 정보는 S3 키에 넣지 않습니다. 분류는 DB 컬럼으로만 관리하며, AI 학습용 목록 순회는 `videos/` 프리픽스 기준으로 수행합니다.
