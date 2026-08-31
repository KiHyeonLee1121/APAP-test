package com.apap.backend.user;

import com.apap.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String name;

    // 구글 계정 고유 ID (OIDC ID 토큰의 sub 클레임). 같은 구글 계정 식별에 사용
    @Column(nullable = false, unique = true, length = 100)
    private String googleSub;

    @Column(length = 500)
    private String pictureUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role = UserRole.MANAGER;

    protected User() {
    }

    public User(String email, String name, String googleSub, String pictureUrl) {
        this.email = email;
        this.name = name;
        this.googleSub = googleSub;
        this.pictureUrl = pictureUrl;
    }

    // 재로그인 시 구글 프로필(이름, 이미지) 최신화
    public void syncProfile(String name, String pictureUrl) {
        this.name = name;
        this.pictureUrl = pictureUrl;
    }

    // 이메일로 찾은 기존 계정에 구글 계정을 연결
    public void linkGoogle(String googleSub) {
        this.googleSub = googleSub;
    }

    // 역할 변경 (관리자에 의한 권한 부여 등)
    public void changeRole(UserRole role) {
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public String getPictureUrl() {
        return pictureUrl;
    }

    public UserRole getRole() {
        return role;
    }
}
