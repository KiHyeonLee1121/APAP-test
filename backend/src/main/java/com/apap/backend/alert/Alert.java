package com.apap.backend.alert;

import com.apap.backend.common.BaseEntity;
import com.apap.backend.event.DetectionEvent;
import com.apap.backend.user.User;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
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
