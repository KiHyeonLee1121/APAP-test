# API 명세 초안

현재 API는 구현 전이며, 아래 내용은 예상 명세 초안입니다. 실제 구현 시 요청/응답 필드와 인증 방식은 변경될 수 있습니다.

## POST /api/events

AI 서버가 이상행동 탐지 이벤트를 백엔드로 전송합니다.

### Request

```json
{
  "eventType": "FALL_DETECTED",
  "cameraId": "CAM-001",
  "detectedAt": "2026-05-07T10:30:00+09:00",
  "confidence": 0.92,
  "snapshotUrl": "https://storage.example.com/events/event-001.jpg"
}
```

### Response

```json
{
  "eventId": "event-001",
  "status": "RECEIVED"
}
```

## GET /api/events

이벤트 목록을 조회합니다.

### Request

```text
GET /api/events?status=OPEN&page=1&size=20
```

### Response

```json
{
  "items": [
    {
      "eventId": "event-001",
      "eventType": "FALL_DETECTED",
      "cameraId": "CAM-001",
      "status": "OPEN",
      "detectedAt": "2026-05-07T10:30:00+09:00"
    }
  ],
  "page": 1,
  "size": 20,
  "totalCount": 1
}
```

## GET /api/events/{eventId}

특정 이벤트의 상세 정보를 조회합니다.

### Request

```text
GET /api/events/event-001
```

### Response

```json
{
  "eventId": "event-001",
  "eventType": "FALL_DETECTED",
  "cameraId": "CAM-001",
  "status": "OPEN",
  "detectedAt": "2026-05-07T10:30:00+09:00",
  "confidence": 0.92,
  "snapshotUrl": "https://storage.example.com/events/event-001.jpg",
  "memo": null
}
```

## PATCH /api/events/{eventId}/status

이벤트 처리 상태를 변경합니다.

### Request

```json
{
  "status": "RESOLVED",
  "memo": "관리자 확인 완료"
}
```

### Response

```json
{
  "eventId": "event-001",
  "status": "RESOLVED",
  "updatedAt": "2026-05-07T11:00:00+09:00"
}
```

## POST /api/auth/login

관리자가 로그인합니다.

### Request

```json
{
  "email": "admin@example.com",
  "password": "password"
}
```

### Response

```json
{
  "accessToken": "jwt_access_token",
  "tokenType": "Bearer"
}
```
