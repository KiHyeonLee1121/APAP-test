package com.apap.backend.auth;

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

import java.time.LocalDateTime;

/**
 * 로그인 이력. 구글 로그인 성공 시마다 1건 기록된다. (7월 회의 결정)
 */
@Entity
@Table(name = "login_history")
public class LoginHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime loggedInAt;

    protected LoginHistory() {
    }

    public LoginHistory(User user) {
        this.user = user;
        this.loggedInAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDateTime getLoggedInAt() {
        return loggedInAt;
    }
}
