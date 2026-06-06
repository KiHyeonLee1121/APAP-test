package com.apap.backend.event;

public enum DetectionEventType {
    // AI 서버 현재 반환값 (binary classification)
    NORMAL,
    ABNORMAL,
    // 향후 AI 모델 고도화 시 사용
    FALL,
    INTRUSION,
    ANOMALOUS,
    UNKNOWN
}
