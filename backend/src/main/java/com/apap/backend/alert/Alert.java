package com.apap.backend.alert;

import com.apap.backend.common.BaseEntity;
import com.apap.backend.event.DetectionEvent;
import com.apap.backend.user.User;
import com.apap.backend.video.VideoSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
// 리셋(숨김) 처리된 알림은 모든 조회에서 자동 제외된다. 행 자체는 DB에 남는다.
@SQLRestriction("deleted = false")
public class Alert extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 테스트/시스템 알림은 특정 감지 이벤트와 연결되지 않을 수 있어 nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detection_event_id")
    private DetectionEvent detectionEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    // 화면 우상단 알림에 "어느 카메라에서 잡혔는지"를 띄우기 위한 값.
    // 연관관계(FK)가 아니라 알림 생성 시점의 스냅샷으로 저장한다 — 영상을 리셋하면
    // video_sources가 조회에서 제외되므로(@SQLRestriction) FK로 걸면 알림 목록까지
    // 함께 깨진다. 알림은 이력이므로 영상이 사라져도 어느 카메라였는지는 남아야 한다.
    // 테스트/시스템 알림은 해당 없음(null).
    @Column(name = "video_source_id")
    private Long videoSourceId;

    @Column(name = "video_source_name", length = 120)
    private String videoSourceName;

    @Column(nullable = false, length = 30)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AlertStatus status;

    @Column(nullable = false)
    private String message;

    private LocalDateTime sentAt;
    private LocalDateTime readAt;

    protected Alert() {
    }

    public Alert(DetectionEvent detectionEvent, User receiver, String message) {
        this.detectionEvent = detectionEvent;
        this.receiver = receiver;
        this.channel = "DASHBOARD";
        this.status = AlertStatus.SENT;
        this.message = message;
        this.sentAt = LocalDateTime.now();
    }

    // 감지 이벤트와 무관한 테스트/시스템 알림용
    public Alert(User receiver, String message) {
        this(null, receiver, message);
    }

    // 실시간 감지 알림용: 감지 이벤트는 없지만 어느 카메라인지는 남긴다
    public Alert(User receiver, VideoSource videoSource, String message) {
        this(null, receiver, message);
        if (videoSource != null) {
            this.videoSourceId = videoSource.getId();
            this.videoSourceName = videoSource.getName();
        }
    }

    public void markAsRead() {
        this.status = AlertStatus.READ;
        this.readAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public DetectionEvent getDetectionEvent() {
        return detectionEvent;
    }

    public User getReceiver() {
        return receiver;
    }

    public Long getVideoSourceId() {
        return videoSourceId;
    }

    public String getVideoSourceName() {
        return videoSourceName;
    }

    public String getChannel() {
        return channel;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }
}
