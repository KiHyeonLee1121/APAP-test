# APAP Frontend

APAP 프로젝트의 프론트엔드 애플리케이션입니다. Vite와 React를 기반으로 Google 로그인, 보호 라우팅, 메인 메뉴, 실시간 영상, 저장된 영상, 알림 내역 화면을 제공합니다.

## 기술 스택

- Vite
- React
- React Router
- ESLint
- Google Identity Services

## 로컬 실행 방법

의존성을 설치합니다.

```bash
npm install
```

프로젝트 루트에 `.env.local` 파일을 만들고 필요한 환경변수를 설정합니다.

```env
VITE_API_BASE_URL=http://localhost:8080
VITE_GOOGLE_CLIENT_ID=여기에_Google_Client_ID를_입력
```

개발 서버를 실행합니다.

```bash
npm run dev
```

## 사용 가능한 스크립트

| 명령어 | 설명 |
| --- | --- |
| `npm run dev` | Vite 개발 서버를 실행합니다. |
| `npm run build` | 배포용 빌드를 생성합니다. |
| `npm run lint` | ESLint 검사를 실행합니다. |
| `npm run preview` | 빌드 결과를 로컬에서 미리 봅니다. |

## 화면 구성 및 라우팅

| 경로 | 화면 | 설명 |
| --- | --- | --- |
| `/` | 로그인 | Google 로그인 화면입니다. |
| `/main` | 메인 | 로그인 후 진입하는 메인 메뉴 화면입니다. |
| `/cctv` | 실시간 영상 | 브라우저 카메라를 사용해 실시간 영상을 표시합니다. |
| `/saved-video` | 저장된 영상 | 정상/비정상 행동 동영상을 추가하고 확인합니다. |
| `/alert` | 알림 내역 | 알림 목록을 확인하고 선택/전체 삭제할 수 있습니다. |

`/main`, `/cctv`, `/saved-video`, `/alert`는 인증이 필요한 보호 라우트입니다. 정의되지 않은 경로는 로그인 화면(`/`)으로 이동합니다.

## API 연동 방식

API 요청은 `VITE_API_BASE_URL`을 기준 주소로 사용합니다. 값 끝의 `/`는 자동으로 제거됩니다.

현재 인증 API는 다음 엔드포인트를 사용합니다.

| 메서드 | 경로 | 용도 |
| --- | --- | --- |
| `POST` | `/api/auth/google` | Google ID 토큰으로 로그인합니다. |
| `GET` | `/api/auth/me` | 현재 로그인한 사용자 정보를 조회합니다. |
| `POST` | `/api/auth/logout` | 현재 액세스 토큰으로 로그아웃합니다. |

인증이 필요한 요청에는 액세스 토큰이 있을 때 `Authorization: Bearer <token>` 헤더가 포함됩니다.
