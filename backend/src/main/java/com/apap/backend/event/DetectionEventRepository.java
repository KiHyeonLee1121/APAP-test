package com.apap.backend.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetectionEventRepository extends JpaRepository<DetectionEvent, Long> {
    List<DetectionEvent> findAllByVideoSourceUserIdOrderByDetectedAtDesc(Long userId);

    long countByVideoSourceUserIdAndEventType(Long userId, DetectionEventType eventType);

    long countByVideoSourceUserIdAndEventTypeIn(Long userId, java.util.Collection<DetectionEventType> eventTypes);
}
