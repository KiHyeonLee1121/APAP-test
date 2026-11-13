package com.apap.backend.cases;

import com.apap.backend.common.BaseEntity;
import com.apap.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 유저-케이스 매핑. 유저가 감지하고자 구독한 케이스를 저장한다. (7월 회의: in user_id, case_id)
 */
@Entity
@Table(name = "user_cases", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "case_id"}))
public class UserCase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private DetectionCase detectionCase;

    @Column(nullable = false)
    private boolean active;

    protected UserCase() {
    }

    public UserCase(User user, DetectionCase detectionCase) {
        this.user = user;
        this.detectionCase = detectionCase;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public DetectionCase getDetectionCase() {
        return detectionCase;
    }

    public boolean isActive() {
        return active;
    }
}
