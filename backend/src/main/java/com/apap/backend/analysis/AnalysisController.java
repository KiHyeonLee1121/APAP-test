package com.apap.backend.analysis;

import com.apap.backend.auth.AuthUser;
import com.apap.backend.common.ApiResponse;
import com.apap.backend.event.DetectionEvent;
import com.apap.backend.event.DetectionEventRepository;
import com.apap.backend.event.DetectionEventType;
import com.apap.backend.event.Severity;
import com.apap.backend.video.VideoSource;
import com.apap.backend.video.VideoSourceRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisJobRepository analysisJobRepository;
    private final VideoSourceRepository videoSourceRepository;
    private final DetectionEventRepository detectionEventRepository;
    private final AnalysisService analysisService;

    public AnalysisController(
            AnalysisJobRepository analysisJobRepository,
            VideoSourceRepository videoSourceRepository,
            DetectionEventRepository detectionEventRepository,
            AnalysisService analysisService
    ) {
        this.analysisJobRepository = analysisJobRepository;
        this.videoSourceRepository = videoSourceRepository;
        this.detectionEventRepository = detectionEventRepository;
        this.analysisService = analysisService;
    }

    /** 수동 분석 요청(재분석 포함). 업로드 시에는 자동 분석이 돌므로 이 API는 재실행 용도. */
    @PostMapping("/jobs")
    public ApiResponse<AnalysisJobResponse> createJob(@Valid @RequestBody AnalysisJobRequest request) {
        VideoSource videoSource = videoSourceRepository.findById(request.videoSourceId())
                .orElseThrow(() -> new EntityNotFoundException("영상 소스를 찾을 수 없습니다."));

        AnalysisJob job = analysisService.analyzeNow(videoSource);
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
            detectionEventRepository.save(new DetectionEvent(
                    job,
                    eventRequest.eventType(),
                    eventRequest.severity(),
                    eventRequest.confidenceScore(),
                    eventRequest.detectedAt(),
                    eventRequest.snapshotUrl(),
                    eventRequest.clipUrl(),
                    eventRequest.resultJson()
            ));
        }

        return ApiResponse.ok(null, "분석 결과가 저장되었습니다.");
    }

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
