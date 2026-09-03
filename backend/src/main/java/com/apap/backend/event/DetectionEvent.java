package com.apap.backend.event;

import com.apap.backend.analysis.AnalysisJob;
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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "detection_events")
// 영상 리셋(숨김) 시 함께 숨겨진 이벤트는 모든 조회·집계에서 자동 제외된다. 행 자체는 DB에 남는다.
@SQLRestriction("deleted = false")
public class DetectionEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id", nullable = false)
    private AnalysisJob analysisJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_source_id", nullable = false)
    private VideoSource videoSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DetectionEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Severity severity;

    @Column(nullable = false)
    private double confidenceScore;

    private LocalDateTime detectedAt;
    private String snapshotUrl;
    private String clipUrl;

    @Lob
    private String resultJson;

    protected DetectionEvent() {
    }

    public DetectionEvent(AnalysisJob analysisJob, DetectionEventType eventType, Severity severity, double confidenceScore, LocalDateTime detectedAt, String snapshotUrl, String clipUrl, String resultJson) {
        this.analysisJob = analysisJob;
        this.videoSource = analysisJob.getVideoSource();
        this.eventType = eventType;
        this.severity = severity;
        this.confidenceScore = confidenceScore;
        this.detectedAt = detectedAt == null ? LocalDateTime.now() : detectedAt;
        this.snapshotUrl = snapshotUrl;
        this.clipUrl = clipUrl;
        this.resultJson = resultJson;
    }

    public Long getId() {
        return id;
    }

    public DetectionEventType getEventType() {
        return eventType;
    }

    public Severity getSeverity() {
        return severity;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public String getSnapshotUrl() {
        return snapshotUrl;
    }

    public String getClipUrl() {
        return clipUrl;
    }

    public String getResultJson() {
        return resultJson;
    }

    public AnalysisJob getAnalysisJob() {
        return analysisJob;
    }

    public VideoSource getVideoSource() {
        return videoSource;
    }
}
