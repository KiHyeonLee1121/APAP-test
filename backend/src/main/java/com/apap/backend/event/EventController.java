package com.apap.backend.event;

import com.apap.backend.auth.AuthUser;
import com.apap.backend.common.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final DetectionEventRepository detectionEventRepository;

    public EventController(DetectionEventRepository detectionEventRepository) {
        this.detectionEventRepository = detectionEventRepository;
    }

    @GetMapping
    public ApiResponse<List<EventResponse>> list(@AuthenticationPrincipal AuthUser authUser) {
        List<EventResponse> events = detectionEventRepository.findAllByVideoSourceUserIdOrderByDetectedAtDesc(authUser.id())
                .stream()
                .map(EventResponse::from)
                .toList();
        return ApiResponse.ok(events);
    }

    @GetMapping("/{eventId}")
    public ApiResponse<EventResponse> get(@PathVariable Long eventId) {
        DetectionEvent event = detectionEventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("이벤트를 찾을 수 없습니다."));
        return ApiResponse.ok(EventResponse.from(event));
    }

    public record EventResponse(
            Long id,
            Long analysisJobId,
            Long videoSourceId,
            DetectionEventType eventType,
            Severity severity,
            double confidenceScore,
            LocalDateTime detectedAt,
            String snapshotUrl,
            String clipUrl,
            String resultJson
    ) {
        static EventResponse from(DetectionEvent event) {
            return new EventResponse(
                    event.getId(),
                    event.getAnalysisJob().getId(),
                    event.getVideoSource().getId(),
                    event.getEventType(),
                    event.getSeverity(),
                    event.getConfidenceScore(),
                    event.getDetectedAt(),
                    event.getSnapshotUrl(),
                    event.getClipUrl(),
                    event.getResultJson()
            );
        }
    }
}
