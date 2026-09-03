package com.apap.backend.analysis;

import com.apap.backend.config.AsyncConfig;
import com.apap.backend.event.DetectionEvent;
import com.apap.backend.event.DetectionEventRepository;
import com.apap.backend.event.DetectionEventType;
import com.apap.backend.event.Severity;
import com.apap.backend.video.VideoSource;
import com.apap.backend.video.VideoSourceRepository;
import com.apap.backend.video.VideoSourceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 분석 실행 로직. 수동 요청(POST /api/analysis/jobs)과
 * 업로드 시 자동 분석이 같은 코드를 사용한다.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final AnalysisJobRepository analysisJobRepository;
    private final VideoSourceRepository videoSourceRepository;
    private final DetectionEventRepository detectionEventRepository;
    private final String aiServerUrl;
    private final RestClient restClient;

    public AnalysisService(
            AnalysisJobRepository analysisJobRepository,
            VideoSourceRepository videoSourceRepository,
            DetectionEventRepository detectionEventRepository,
            @Value("${apap.ai-server-url}") String aiServerUrl
    ) {
        this.analysisJobRepository = analysisJobRepository;
        this.videoSourceRepository = videoSourceRepository;
        this.detectionEventRepository = detectionEventRepository;
        this.aiServerUrl = aiServerUrl;
        this.restClient = RestClient.create();
    }

    /** 수동 분석 요청: 작업을 만들고 즉시(동기) 실행한다. */
    public AnalysisJob analyzeNow(VideoSource videoSource) {
        AnalysisJob job = analysisJobRepository.save(new AnalysisJob(videoSource));
        execute(job, videoSource);
        return analysisJobRepository.save(job);
    }

    /** 자동 분석용: 작업만 먼저 만들고 영상 상태를 ANALYZING으로 바꾼다. */
    public AnalysisJob createPendingJob(VideoSource videoSource) {
        AnalysisJob job = analysisJobRepository.save(new AnalysisJob(videoSource));
        videoSource.changeStatus(VideoSourceStatus.ANALYZING);
        videoSourceRepository.save(videoSource);
        return job;
    }

    /**
     * 자동 분석 실행(백그라운드). 업로드 응답을 막지 않기 위해 별도 스레드에서 돈다.
     * 스레드가 다르므로 엔티티를 넘기지 않고 id로 다시 조회한다.
     */
    @Async(AsyncConfig.ANALYSIS_EXECUTOR)
    public void runAsync(Long jobId) {
        // videoSource/user는 LAZY라 세션 밖에서 접근하면 실패한다. 함께 로딩해 둔다.
        AnalysisJob job = analysisJobRepository.findWithVideoAndUserById(jobId).orElse(null);
        if (job == null) {
            log.warn("자동 분석 대상 작업을 찾을 수 없습니다. jobId={}", jobId);
            return;
        }
        VideoSource videoSource = job.getVideoSource();

        execute(job, videoSource);
        analysisJobRepository.save(job);

        videoSource.changeStatus(job.getStatus() == AnalysisJobStatus.DONE
                ? VideoSourceStatus.READY
                : VideoSourceStatus.ERROR);
        videoSourceRepository.save(videoSource);
    }

    /** AI 서버 호출 → DetectionEvent 저장 → 작업 상태 확정 */
    private void execute(AnalysisJob job, VideoSource videoSource) {
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

                detectionEventRepository.save(new DetectionEvent(
                        job, eventType, resolveSeverity(aiResponse.confidence()),
                        aiResponse.confidence(),
                        LocalDateTime.now(),
                        null, null, null
                ));

                job.complete(AnalysisJobStatus.DONE, null);
            } else {
                String errorMsg = aiResponse != null ? aiResponse.message() : "AI 서버 응답 없음";
                job.complete(AnalysisJobStatus.FAILED, errorMsg);
            }
        } catch (Exception e) {
            job.complete(AnalysisJobStatus.FAILED, "AI 서버 호출 실패: " + e.getMessage());
        }
    }

    public Severity resolveSeverity(double confidence) {
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
}
