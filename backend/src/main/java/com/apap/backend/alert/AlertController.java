package com.apap.backend.alert;

import com.apap.backend.auth.AuthUser;
import com.apap.backend.cases.UserCaseRepository;
import com.apap.backend.common.ApiResponse;
import com.apap.backend.user.User;
import com.apap.backend.user.UserRepository;
import com.apap.backend.video.VideoSource;
import com.apap.backend.video.VideoSourceRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private static final String DEFAULT_LIVE_ALERT_MESSAGE = "비정상 행동이 감지되었습니다.";

    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final VideoSourceRepository videoSourceRepository;
    private final UserCaseRepository userCaseRepository;

    public AlertController(
            AlertRepository alertRepository,
            UserRepository userRepository,
            VideoSourceRepository videoSourceRepository,
            UserCaseRepository userCaseRepository
    ) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.videoSourceRepository = videoSourceRepository;
        this.userCaseRepository = userCaseRepository;
    }

    @GetMapping
    public ApiResponse<List<AlertResponse>> list(@AuthenticationPrincipal AuthUser authUser) {
        List<AlertResponse> alerts = alertRepository.findAllByReceiverIdOrderByIdDesc(authUser.id())
                .stream()
                .map(AlertResponse::from)
                .toList();
        return ApiResponse.ok(alerts);
    }

    @PatchMapping("/{alertId}/read")
    public ApiResponse<AlertResponse> read(@PathVariable Long alertId,
                                           @AuthenticationPrincipal AuthUser authUser) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new EntityNotFoundException("알림을 찾을 수 없습니다."));
        if (!alert.getReceiver().getId().equals(authUser.id())) {
            throw new EntityNotFoundException("알림을 찾을 수 없습니다.");
        }
        alert.markAsRead();
        return ApiResponse.ok(AlertResponse.from(alertRepository.save(alert)));
    }

    /**
     * 알림 리셋. 읽음/안읽음 구분 없이 내 알림을 전부 화면에서 감춘다.
     * DB 행은 지우지 않고 숨김 표시만 하므로 이력은 서버에 남는다.
     * 영상 리셋(POST /api/videos/reset)과는 독립적으로 동작한다.
     */
    @PostMapping("/reset")
    @Transactional
    public ApiResponse<ResetResponse> reset(@AuthenticationPrincipal AuthUser authUser) {
        int hiddenCount = alertRepository.hideAllByReceiverId(authUser.id());
        return ApiResponse.ok(new ResetResponse(hiddenCount),
                "알림 " + hiddenCount + "건을 목록에서 숨겼습니다. 데이터는 서버에 보관됩니다.");
    }

    /** 테스트 알림 발송: 현재 사용자에게 대시보드 알림 한 건을 생성한다. */
    @PostMapping("/test")
    public ApiResponse<AlertResponse> test(@AuthenticationPrincipal AuthUser authUser) {
        User receiver = userRepository.findById(authUser.id())
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        Alert alert = alertRepository.save(new Alert(receiver, "테스트 알림입니다."));
        return ApiResponse.ok(AlertResponse.from(alert), "테스트 알림이 발송되었습니다.");
    }

    /**
     * 실시간 감지 알림: AI 서버가 카메라별로 알림 여부를 이미 판단(5연속 감지 +
     * 짧은 재발동 쿨다운)한 뒤 호출하므로, 여기서는 중복 억제 없이 그대로 저장한다.
     * 인증 사용자가 없는 서버 간 호출이라 videoSourceId로 수신자(카메라 소유자)를 정한다.
     */
    @PostMapping("/live")
    public ApiResponse<AlertResponse> live(@Valid @RequestBody LiveAlertRequest request) {
        VideoSource videoSource = videoSourceRepository.findById(request.videoSourceId())
                .orElseThrow(() -> new EntityNotFoundException("영상 소스를 찾을 수 없습니다."));
        Alert alert = alertRepository.save(
                new Alert(videoSource.getUser(), resolveMessage(request, videoSource.getUser().getId())));
        return ApiResponse.ok(AlertResponse.from(alert), "실시간 알림이 생성되었습니다.");
    }

    /**
     * 알림 문구 결정 순서 (7월 회의: in user_id, case_id → out msg)
     * 1) AI 서버가 보낸 message가 있으면 그대로 사용
     * 2) 없으면 수신자가 구독한 활성 케이스의 out_msg 사용(여러 개면 가장 먼저 등록한 것)
     * 3) 그것도 없으면 기본 문구
     */
    private String resolveMessage(LiveAlertRequest request, Long receiverId) {
        if (StringUtils.hasText(request.message())) {
            return request.message();
        }
        return userCaseRepository.findFirstByUserIdAndActiveIsTrueOrderByIdAsc(receiverId)
                .map(userCase -> userCase.getDetectionCase().getOutMsg())
                .orElse(DEFAULT_LIVE_ALERT_MESSAGE);
    }

    public record LiveAlertRequest(@NotNull Long videoSourceId, String message) {
    }

    /** 리셋 결과: 화면에서 숨긴 건수 (DB 행은 삭제되지 않음) */
    public record ResetResponse(int hiddenCount) {
    }

    public record AlertResponse(
            Long id,
            Long detectionEventId,
            Long receiverId,
            String channel,
            AlertStatus status,
            String message,
            LocalDateTime sentAt,
            LocalDateTime readAt
    ) {
        static AlertResponse from(Alert alert) {
            return new AlertResponse(
                    alert.getId(),
                    alert.getDetectionEvent() == null ? null : alert.getDetectionEvent().getId(),
                    alert.getReceiver().getId(),
                    alert.getChannel(),
                    alert.getStatus(),
                    alert.getMessage(),
                    alert.getSentAt(),
                    alert.getReadAt()
            );
        }
    }
}
