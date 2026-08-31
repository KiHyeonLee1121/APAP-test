# APAP ERD (Entity-Relationship Diagram)

백엔드 구현(JPA 엔티티) 기준의 데이터 모델입니다. DB: MySQL 8, 테이블은 Hibernate `ddl-auto: update`로 생성됩니다.

## ERD (Mermaid)

> GitHub에서 자동 렌더링됩니다.

```mermaid
erDiagram
    users ||--o{ video_sources : "소유"
    video_sources ||--o{ analysis_jobs : "분석 대상"
    analysis_jobs ||--o{ detection_events : "결과 생성"
    video_sources ||--o{ detection_events : "발생 위치"
    detection_events |o--o{ alerts : "알림 트리거(선택)"
    users ||--o{ alerts : "수신"
    users ||--o{ login_history : "로그인 이력"
    users ||--o{ user_cases : "케이스 구독"
    detection_cases ||--o{ user_cases : "구독 대상"

    login_history {
        BIGINT id PK
        BIGINT user_id FK "NOT NULL -> users.id"
        DATETIME logged_in_at "NOT NULL, 로그인 시각"
        DATETIME created_at
        DATETIME updated_at
        BOOLEAN deleted
    }

    detection_cases {
        BIGINT id PK
        VARCHAR(100) name UK "NOT NULL, 케이스명"
        VARCHAR(300) description
        VARCHAR(300) out_msg "NOT NULL, 비정상 판정 시 알림 메시지"
        DATETIME created_at
        DATETIME updated_at
        BOOLEAN deleted
    }

    user_cases {
        BIGINT id PK
        BIGINT user_id FK "NOT NULL -> users.id"
        BIGINT case_id FK "NOT NULL -> detection_cases.id"
        BOOLEAN active "NOT NULL, (user_id, case_id) UNIQUE"
        DATETIME created_at
        DATETIME updated_at
        BOOLEAN deleted
    }

    users {
        BIGINT id PK "AUTO_INCREMENT"
        VARCHAR(255) email UK "NOT NULL"
        VARCHAR(100) name "NOT NULL"
        VARCHAR(100) google_sub UK "NOT NULL, 구글 OIDC sub"
        VARCHAR(500) picture_url "구글 프로필 이미지"
        VARCHAR(30) role "ADMIN | MANAGER | VIEWER"
        DATETIME created_at "NOT NULL"
        DATETIME updated_at "NOT NULL"
        BOOLEAN deleted "soft delete"
    }

    video_sources {
        BIGINT id PK
        BIGINT user_id FK "NOT NULL -> users.id"
        VARCHAR(30) type "UPLOAD | CCTV | EDGE_GATEWAY"
        VARCHAR(120) name "NOT NULL"
        VARCHAR(500) source_url "NOT NULL, 파일경로/스트림URL"
        VARCHAR(30) status "READY | ANALYZING | ERROR | DISABLED"
        DATETIME created_at
        DATETIME updated_at
        BOOLEAN deleted "soft delete 적용(@SQLRestriction)"
    }

    analysis_jobs {
        BIGINT id PK
        BIGINT video_source_id FK "NOT NULL -> video_sources.id"
        VARCHAR(30) status "PENDING | RUNNING | DONE | FAILED"
        DATETIME requested_at
        DATETIME completed_at
        VARCHAR(255) error_message
        DATETIME created_at
        DATETIME updated_at
        BOOLEAN deleted
    }

    detection_events {
        BIGINT id PK
        BIGINT analysis_job_id FK "NOT NULL -> analysis_jobs.id"
        BIGINT video_source_id FK "NOT NULL -> video_sources.id"
        VARCHAR(30) event_type "NORMAL | ABNORMAL | FALL | INTRUSION | ANOMALOUS | UNKNOWN"
        VARCHAR(30) severity "LOW | MEDIUM | HIGH | CRITICAL"
        DOUBLE confidence_score "NOT NULL"
        DATETIME detected_at
        VARCHAR(255) snapshot_url
        VARCHAR(255) clip_url
        LONGTEXT result_json "AI 원본 결과(@Lob)"
        DATETIME created_at
        DATETIME updated_at
        BOOLEAN deleted
    }

    alerts {
        BIGINT id PK
        BIGINT detection_event_id FK "NULL 허용 -> detection_events.id (테스트/시스템 알림)"
        BIGINT receiver_id FK "NOT NULL -> users.id"
        VARCHAR(30) channel "DASHBOARD (추후 EMAIL/SMS/WEB_PUSH)"
        VARCHAR(30) status "PENDING | SENT | FAILED | READ"
        VARCHAR(255) message "NOT NULL"
        DATETIME sent_at
        DATETIME read_at
        DATETIME created_at
        DATETIME updated_at
        BOOLEAN deleted
    }
```

## 관계 요약

| 관계 | 카디널리티 | 설명 |
|---|---|---|
| users → video_sources | 1 : N | 사용자가 영상 소스를 소유 |
| video_sources → analysis_jobs | 1 : N | 영상 하나에 여러 분석 작업 |
| analysis_jobs → detection_events | 1 : N | 분석 결과로 이벤트 생성 |
| video_sources → detection_events | 1 : N | 이벤트 발생 영상 (역정규화 참조) |
| detection_events → alerts | 0..1 : N | 이상 이벤트 시 알림 (테스트 알림은 이벤트 없이 생성 가능) |
| users → alerts | 1 : N | 알림 수신자 |
| users → login_history | 1 : N | 구글 로그인 성공 시마다 1건 기록 (7월 회의) |
| users → user_cases ← detection_cases | N : M (매핑 테이블) | 유저의 감지 케이스 구독. 비정상 판정 시 활성 케이스의 `out_msg`를 알림 메시지로 사용 (7월 회의) |

## 설계 노트

- **공통 컬럼**: 모든 테이블에 `created_at`, `updated_at`, `deleted`(soft delete) — `BaseEntity` 상속.
- **소유권 체인**: `users → video_sources → analysis_jobs → detection_events` — 조회 시 이 체인으로 "내 데이터"만 필터링 (예: `findAllByVideoSourceUserId...`).
- **scenario 없음**: 초기 설계의 scenario 도메인은 AI 서버 실제 스펙(normal/abnormal 이진 분류)에 맞춰 제거됨.
- **enum은 문자열 저장**: `@Enumerated(EnumType.STRING)` — DB에서 읽기 쉽고 순서 변경에 안전.
- **alerts.detection_event_id nullable**: 테스트/시스템 알림 지원.
- **video_sources.deleted**: `@SQLDelete`로 삭제 시 UPDATE, `@SQLRestriction`으로 조회 자동 제외.

## dbdiagram.io 용 DBML

> https://dbdiagram.io 에 붙여넣으면 시각 ERD로 렌더링/이미지 내보내기 가능 (보고서용).

```dbml
Table users {
  id bigint [pk, increment]
  email varchar(255) [not null, unique]
  name varchar(100) [not null]
  google_sub varchar(100) [not null, unique, note: '구글 OIDC sub']
  picture_url varchar(500)
  role varchar(30) [not null, note: 'ADMIN | MANAGER | VIEWER']
  created_at datetime [not null]
  updated_at datetime [not null]
  deleted boolean [not null, default: false]
}

Table video_sources {
  id bigint [pk, increment]
  user_id bigint [not null, ref: > users.id]
  type varchar(30) [not null, note: 'UPLOAD | CCTV | EDGE_GATEWAY']
  name varchar(120) [not null]
  source_url varchar(500) [not null]
  status varchar(30) [not null, note: 'READY | ANALYZING | ERROR | DISABLED']
  created_at datetime [not null]
  updated_at datetime [not null]
  deleted boolean [not null, default: false]
}

Table analysis_jobs {
  id bigint [pk, increment]
  video_source_id bigint [not null, ref: > video_sources.id]
  status varchar(30) [not null, note: 'PENDING | RUNNING | DONE | FAILED']
  requested_at datetime
  completed_at datetime
  error_message varchar(255)
  created_at datetime [not null]
  updated_at datetime [not null]
  deleted boolean [not null, default: false]
}

Table detection_events {
  id bigint [pk, increment]
  analysis_job_id bigint [not null, ref: > analysis_jobs.id]
  video_source_id bigint [not null, ref: > video_sources.id]
  event_type varchar(30) [not null, note: 'NORMAL | ABNORMAL | FALL | INTRUSION | ANOMALOUS | UNKNOWN']
  severity varchar(30) [not null, note: 'LOW | MEDIUM | HIGH | CRITICAL']
  confidence_score double [not null]
  detected_at datetime
  snapshot_url varchar(255)
  clip_url varchar(255)
  result_json longtext
  created_at datetime [not null]
  updated_at datetime [not null]
  deleted boolean [not null, default: false]
}

Table alerts {
  id bigint [pk, increment]
  detection_event_id bigint [ref: > detection_events.id, note: 'NULL = 테스트/시스템 알림']
  receiver_id bigint [not null, ref: > users.id]
  channel varchar(30) [not null, default: 'DASHBOARD']
  status varchar(30) [not null, note: 'PENDING | SENT | FAILED | READ']
  message varchar(255) [not null]
  sent_at datetime
  read_at datetime
  created_at datetime [not null]
  updated_at datetime [not null]
  deleted boolean [not null, default: false]
}
```
