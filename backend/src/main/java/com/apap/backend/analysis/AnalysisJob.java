package com.apap.backend.analysis;

import com.apap.backend.common.BaseEntity;
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
@Table(name = "analysis_jobs")
// 영상 리셋(숨김) 시 함께 숨겨진 작업은 모든 조회·집계에서 자동 제외된다. 행 자체는 DB에 남는다.
@SQLRestriction("deleted = false")
public class AnalysisJob extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_source_id", nullable = false)
    private VideoSource videoSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AnalysisJobStatus status;

    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    private String errorMessage;

    protected AnalysisJob() {
    }

    public AnalysisJob(VideoSource videoSource) {
        this.videoSource = videoSource;
        this.status = AnalysisJobStatus.PENDING;
        this.requestedAt = LocalDateTime.now();
    }

    public void complete(AnalysisJobStatus status, String errorMessage) {
        this.status = status;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public VideoSource getVideoSource() {
        return videoSource;
    }

    public AnalysisJobStatus getStatus() {
        return status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
