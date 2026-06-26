package com.apap.backend.dashboard;

import com.apap.backend.alert.AlertRepository;
import com.apap.backend.alert.AlertStatus;
import com.apap.backend.analysis.AnalysisJobRepository;
import com.apap.backend.auth.AuthUser;
import com.apap.backend.common.ApiResponse;
import com.apap.backend.event.DetectionEvent;
import com.apap.backend.event.DetectionEventRepository;
import com.apap.backend.event.DetectionEventType;
import com.apap.backend.event.Severity;
import com.apap.backend.video.VideoSourceRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final VideoSourceRepository videoSourceRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final DetectionEventRepository detectionEventRepository;
    private final AlertRepository alertRepository;

    public DashboardController(
            VideoSourceRepository videoSourceRepository,
            AnalysisJobRepository analysisJobRepository,
            DetectionEventRepository detectionEventRepository,
            AlertRepository alertRepository
    ) {
        this.videoSourceRepository = videoSourceRepository;
        this.analysisJobRepository = analysisJobRepository;
        this.detectionEventRepository = detectionEventRepository;
        this.alertRepository = alertRepository;
    }

    @GetMapping("/summary")
    public ApiResponse<DashboardSummary> summary(@AuthenticationPrincipal AuthUser authUser) {
        Long userId = authUser.id();
        return ApiResponse.ok(new DashboardSummary(
                videoSourceRepository.findAllByUserIdOrderByIdDesc(userId).size(),
                analysisJobRepository.findAllByVideoSourceUserIdOrderByIdDesc(userId).size(),
                detectionEventRepository.countByVideoSourceUserIdAndEventTypeIn(userId, java.util.List.of(DetectionEventType.ABNORMAL, DetectionEventType.FALL, DetectionEventType.INTRUSION, DetectionEventType.ANOMALOUS)),
                alertRepository.countByReceiverIdAndStatusNot(userId, AlertStatus.READ)
        ));
    }

    /** 날짜별 감지 이벤트 건수 (시간대별 추이) */
    @GetMapping("/timeline")
    public ApiResponse<List<TimelinePoint>> timeline(@AuthenticationPrincipal AuthUser authUser) {
        Map<LocalDate, Long> byDate = detectionEventRepository
                .findAllByVideoSourceUserIdOrderByDetectedAtDesc(authUser.id())
                .stream()
                .filter(event -> event.getDetectedAt() != null)
                .collect(Collectors.groupingBy(
                        event -> event.getDetectedAt().toLocalDate(),
                        TreeMap::new,
                        Collectors.counting()));
        List<TimelinePoint> points = byDate.entrySet().stream()
                .map(entry -> new TimelinePoint(entry.getKey().toString(), entry.getValue()))
                .toList();
        return ApiResponse.ok(points);
    }

    /** 심각도별 감지 이벤트 건수 (LOW/MEDIUM/HIGH/CRITICAL 전부 포함, 없으면 0) */
    @GetMapping("/severity")
    public ApiResponse<List<SeverityCount>> severity(@AuthenticationPrincipal AuthUser authUser) {
        Map<Severity, Long> bySeverity = detectionEventRepository
                .findAllByVideoSourceUserIdOrderByDetectedAtDesc(authUser.id())
                .stream()
                .collect(Collectors.groupingBy(DetectionEvent::getSeverity, Collectors.counting()));
        List<SeverityCount> counts = java.util.Arrays.stream(Severity.values())
                .map(severity -> new SeverityCount(severity, bySeverity.getOrDefault(severity, 0L)))
                .toList();
        return ApiResponse.ok(counts);
    }

    public record DashboardSummary(
            long videos,
            long analysisJobs,
            long abnormalEvents,
            long unreadAlerts
    ) {
    }

    public record TimelinePoint(String date, long count) {
    }

    public record SeverityCount(Severity severity, long count) {
    }
}
