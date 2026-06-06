package com.apap.backend.alert;

import com.apap.backend.auth.AuthUser;
import com.apap.backend.common.ApiResponse;
import com.apap.backend.user.User;
import com.apap.backend.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRepository alertRepository;
    private final UserRepository userRepository;

    public AlertController(AlertRepository alertRepository, UserRepository userRepository) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
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

    /** 테스트 알림 발송: 현재 사용자에게 대시보드 알림 한 건을 생성한다. */
    @PostMapping("/test")
    public ApiResponse<AlertResponse> test(@AuthenticationPrincipal AuthUser authUser) {
        User receiver = userRepository.findById(authUser.id())
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        Alert alert = alertRepository.save(new Alert(receiver, "테스트 알림입니다."));
        return ApiResponse.ok(AlertResponse.from(alert), "테스트 알림이 발송되었습니다.");
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
