package com.apap.backend.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 실시간 감지 알림을 화면에 즉시 밀어 주기 위한 SSE 연결 보관소.
 *
 * 폴링(GET /api/alerts/unread-count)만 쓰면 새 알림이 떠도 최대 폴링 주기만큼
 * 늦게 보인다. 감지 즉시 우상단에 알림을 띄우려면 서버가 먼저 보내야 해서
 * 화면이 열어 둔 연결을 사용자별로 들고 있다가 알림이 생기는 순간 내보낸다.
 *
 * 폴링 API는 그대로 두었다. SSE가 끊긴 동안에는 화면이 폴링으로 메울 수 있다.
 */
@Service
public class AlertStreamService {

    private static final Logger log = LoggerFactory.getLogger(AlertStreamService.class);

    /** 연결 최대 유지 시간. 만료되면 클라이언트(EventSource/fetch)가 자동 재연결한다. */
    private static final long STREAM_TIMEOUT_MS = 30 * 60 * 1000L;

    /** 새 알림이 도착했을 때 쓰는 이벤트 이름. 화면에서 이 이름으로 구독한다. */
    public static final String EVENT_ALERT = "alert";

    /** 한 사용자가 여러 탭을 열 수 있으므로 userId 하나에 연결 여러 개를 둔다. */
    private final Map<Long, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    /** 화면이 알림 스트림을 구독한다. 반환한 emitter를 컨트롤러가 그대로 내려보낸다. */
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        emittersByUser.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));

        // 연결 직후 한 건을 보내야 프록시가 응답을 흘려보내고, 화면도 연결된 것을 안다.
        send(userId, emitter, "connected", Map.of("connectedAt", LocalDateTime.now().toString()));
        return emitter;
    }

    /** 수신자의 열려 있는 모든 화면에 알림을 내보낸다. 연결이 없으면 아무 일도 하지 않는다. */
    public void publish(Long receiverId, Object payload) {
        List<SseEmitter> emitters = emittersByUser.get(receiverId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            send(receiverId, emitter, EVENT_ALERT, payload);
        }
    }

    /**
     * 유휴 연결이 중간 장비에 끊기지 않도록 주기적으로 주석(comment)을 보낸다.
     * 주석은 이벤트가 아니라서 화면 쪽 핸들러를 건드리지 않는다.
     */
    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        emittersByUser.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (IOException | IllegalStateException e) {
                    // 이미 닫힌 연결이다. 조용히 정리한다.
                    remove(userId, emitter);
                }
            }
        });
    }

    /** 현재 열려 있는 연결 수 (테스트·점검용) */
    public int activeConnections(Long userId) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        return emitters == null ? 0 : emitters.size();
    }

    private void send(Long userId, SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException | IllegalStateException e) {
            // 화면을 닫았거나 새로고침한 경우다. 실패한 연결만 버리고 나머지는 유지한다.
            log.debug("알림 스트림 전송 실패로 연결을 정리합니다. userId={}", userId);
            remove(userId, emitter);
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUser.remove(userId);
        }
    }
}
