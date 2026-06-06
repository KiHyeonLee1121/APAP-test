package com.apap.backend.video;

import com.apap.backend.common.BaseEntity;
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
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "video_sources")
@SQLDelete(sql = "UPDATE video_sources SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
public class VideoSource extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VideoSourceType type;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 500)
    private String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VideoSourceStatus status;

    protected VideoSource() {
    }

    public VideoSource(User user, VideoSourceType type, String name, String sourceUrl) {
        this.user = user;
        this.type = type;
        this.name = name;
        this.sourceUrl = sourceUrl;
        this.status = VideoSourceStatus.READY;
    }

    public void update(VideoSourceType type, String name, String sourceUrl, VideoSourceStatus status) {
        this.type = type;
        this.name = name;
        this.sourceUrl = sourceUrl;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public VideoSourceType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public VideoSourceStatus getStatus() {
        return status;
    }
}
