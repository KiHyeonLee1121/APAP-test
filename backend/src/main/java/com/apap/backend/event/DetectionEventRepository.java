package com.apap.backend.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DetectionEventRepository extends JpaRepository<DetectionEvent, Long> {
    List<DetectionEvent> findAllByVideoSourceUserIdOrderByDetectedAtDesc(Long userId);

    long countByVideoSourceUserIdAndEventType(Long userId, DetectionEventType eventType);

    long countByVideoSourceUserIdAndEventTypeIn(Long userId, java.util.Collection<DetectionEventType> eventTypes);

    /** 영상 리셋 시, 그 사용자의 영상에서 나온 감지 이벤트도 함께 숨긴다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE detection_events SET deleted = true, updated_at = CURRENT_TIMESTAMP "
            + "WHERE deleted = false AND video_source_id IN "
            + "(SELECT id FROM video_sources WHERE user_id = :userId)", nativeQuery = true)
    int hideAllByUserId(@Param("userId") Long userId);
}
