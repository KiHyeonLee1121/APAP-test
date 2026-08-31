package com.apap.backend.analysis;

import com.apap.backend.alert.Alert;
import com.apap.backend.alert.AlertRepository;
import com.apap.backend.auth.AuthUser;
import com.apap.backend.cases.UserCaseRepository;
import com.apap.backend.common.ApiResponse;
import com.apap.backend.event.DetectionEvent;
import com.apap.backend.event.DetectionEventRepository;
import com.apap.backend.event.DetectionEventType;
import com.apap.backend.event.Severity;
import com.apap.backend.video.VideoSource;
import com.apap.backend.video.VideoSourceRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisJobRepository analysisJobRepository;
    private final VideoSourceRepository videoSourceRepository;
    private final DetectionEventRepository detectionEventRepository;
    private final AlertRepository alertRepository;
    private final UserCaseRepository userCaseRepository;
    private final String aiServerUrl;
    private final RestClient restClient;

    public AnalysisController(
            AnalysisJobRepository analysisJobRepository,
            VideoSourceRepository videoSourceRepository,
            DetectionEventRepository detectionEventRepository,
            AlertRepository alertRepository,
            UserCaseRepository userCaseRepository,
            @Value("${apap.ai-server-url}") String aiServerUrl
    ) {
        this.analysisJobRepository = analysisJobRepository;
        this.videoSourceRepository = videoSourceRepository;
        this.detectionEventRepository = detectionEventRepository;
        this.alertRepository = alertRepository;
        this.userCaseRepository = userCaseRepository;
        this.aiServerUrl = aiServerUrl;
        this.restClient = RestClient.create();
    }

    /**
     * 알림 메시지 결정: 유저에게 활성 케이스가 있으면 해당 케이스의 out_msg를 사용하고,
     * 없으면 기본 메시지를 유지한다. (7월 회의: 케이스 out_msg 연계, 여러 개면 가장 먼저 등록한 활성 케이스 기준)
     */
    private String resolveAlertMessage(Long userId, String defaultMessage) {
        return userCaseRepository.findFirstByUserIdAndActiveIsTrueOrderByIdAsc(userId)
                .map(userCase -> userCase.getDetectionCase().getOutMsg())
                .orElse(defaultMessage);
    }

    @PostMapping("/jobs")
    public ApiResponse<AnalysisJobResponse> createJob(@Valid @RequestBody AnalysisJobRequest request) {
        VideoSource videoSource = videoSourceRepository.findById(request.videoSourceId())
                .orElseThrow(() -> new EntityNotFoundException("영상 소스를 찾을 수 없습니다."));

        AnalysisJob job = analysisJobRepository.save(new AnalysisJob(videoSource));

        // AI 서버에 동기 호출: POST /predict/video
        try {
            Map<String, Object> aiRequest = new HashMap<>();
            aiRequest.put("video_path", videoSource.getSourceUrl());

            AiPredictionResponse aiResponse = restClient.post()
                    .uri(aiServerUrl + "/predict/video")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(aiRequest)
                    .retrieve()
                    .body(AiPredictionResponse.class);

            if (aiResponse != null && "success".equals(aiResponse.status())) {
                DetectionEventType eventType = "abnormal".equalsIgnoreCase(aiResponse.prediction())
                        ? DetectionEventType.ABNORMAL
                        : DetectionEventType.NORMAL;

                Severity severity = resolveSeverity(aiResponse.confidence());

                DetectionEvent event = detectionEventRepository.save(new DetectionEvent(
                        job, eventType, severity,
                        aiResponse.confidence(),
                        LocalDateTime.now(),
                        null, null, null
                ));

                if (eventType == DetectionEventType.ABNORMAL) {
                    String message = resolveAlertMessage(
                            videoSource.getUser().getId(),
                            "비정상 행동이 감지되었습니다. confidence=" + String.format("%.2f", aiResponse.confidence()));
                    alertRepository.save(new Alert(event, videoSource.getUser(), message));
                }

                job.complete(AnalysisJobStatus.DONE, null);
            } else {
                String errorMsg = aiResponse != null ? aiResponse.message() : "AI 서버 응답 없음";
                job.complete(AnalysisJobStatus.FAILED, errorMsg);
            }
        } catch (Exception e) {
            job.complete(AnalysisJobStatus.FAILED, "AI 서버 호출 실패: " + e.getMessage());
        }

        analysisJobRepository.save(job);
        return ApiResponse.ok(AnalysisJobResponse.from(job), "분석이 완료되었습니다.");
    }

    @GetMapping("/jobs")
    public ApiResponse<List<AnalysisJobResponse>> listJobs(@AuthenticationPrincipal AuthUser authUser) {
        List<AnalysisJobResponse> jobs = analysisJobRepository.findAllByVideoSourceUserIdOrderByIdDesc(authUser.id())
                .stream()
                .map(AnalysisJobResponse::from)
                .toList();
        return ApiResponse.ok(jobs);
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResponse<AnalysisJobResponse> getJob(@PathVariable Long jobId) {
        AnalysisJob job = analysisJobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("분석 작업을 찾을 수 없습니다."));
        return ApiResponse.ok(AnalysisJobResponse.from(job));
    }

    // AI 서버가 아닌 엣지 디바이스(CCTV/카메라)에서 직접 결과를 전송하는 경우 사용
    @PostMapping("/callback")
    public ApiResponse<Void> callback(@Valid @RequestBody AnalysisCallbackRequest request) {
        AnalysisJob job = analysisJobRepository.findById(request.jobId())
                .orElseThrow(() -> new EntityNotFoundException("분석 작업을 찾을 수 없습니다."));
        job.complete(request.status(), request.errorMessage());
        analysisJobRepository.save(job);

        for (DetectionEventRequest eventRequest : request.events()) {
            DetectionEvent event = detectionEventRepository.save(new DetectionEvent(
                    job,
                    eventRequest.eventType(),
                    eventRequest.severity(),
                    eventRequest.confidenceScore(),
                    eventRequest.detectedAt(),
                    eventRequest.snapshotUrl(),
                    eventRequest.clipUrl(),
                    eventRequest.resultJson()
            ));

            if (eventRequest.eventType() == DetectionEventType.ABNORMAL
                    || eventRequest.eventType() == DetectionEventType.FALL
                    || eventRequest.eventType() == DetectionEventType.INTRUSION
                    || eventRequest.eventType() == DetectionEventType.ANOMALOUS) {
                String message = resolveAlertMessage(
                        job.getVideoSource().getUser().getId(),
                        "비정상 행동이 감지되었습니다. severity=" + eventRequest.severity());
                alertRepository.save(new Alert(event, job.getVideoSource().getUser(), message));
            }
        }

        return ApiResponse.ok(null, "분석 결과가 저장되었습니다.");
    }

    private Severity resolveSeverity(double confidence) {
        if (confidence >= 0.9) return Severity.CRITICAL;
        if (confidence >= 0.75) return Severity.HIGH;
        if (confidence >= 0.5) return Severity.MEDIUM;
        return Severity.LOW;
    }

    public record AiPredictionResponse(
            String prediction,
            double confidence,
            String source,
            String status,
            String message
    ) {}

    public record AnalysisJobRequest(Long videoSourceId) {}

    public record AnalysisJobResponse(
            Long id,
            Long videoSourceId,
            AnalysisJobStatus status,
            LocalDateTime requestedAt,
            LocalDateTime completedAt,
            String errorMessage
    ) {
        static AnalysisJobResponse from(AnalysisJob job) {
            return new AnalysisJobResponse(
                    job.getId(),
                    job.getVideoSource().getId(),
                    job.getStatus(),
                    job.getRequestedAt(),
                    job.getCompletedAt(),
                    job.getErrorMessage()
            );
        }
    }

    public record AnalysisCallbackRequest(
            Long jobId,
            AnalysisJobStatus status,
            String errorMessage,
            List<DetectionEventRequest> events
    ) {}

    public record DetectionEventRequest(
            DetectionEventType eventType,
            Severity severity,
            double confidenceScore,
            LocalDateTime detectedAt,
            String snapshotUrl,
            String clipUrl,
            String resultJson
    ) {}
}
