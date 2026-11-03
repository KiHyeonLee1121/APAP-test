package com.apap.backend.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findAllByReceiverIdOrderByIdDesc(Long receiverId);

    long countByReceiverIdAndStatusNot(Long receiverId, AlertStatus status);

    /** 실시간 화면 폴링용: 마지막으로 받은 알림 이후에 새로 생긴 것만 조회한다. */
    List<Alert> findAllByReceiverIdAndIdGreaterThanOrderByIdDesc(Long receiverId, Long sinceId);

    /** 새 알림 도착 여부를 싸게 확인하기 위한 최신 알림 id */
    Optional<Alert> findFirstByReceiverIdOrderByIdDesc(Long receiverId);

    /**
     * 알림 리셋: 해당 사용자가 받은 알림을 한 번에 숨긴다(deleted=true).
     * 읽음/안읽음 구분 없이 전부 대상이며, 행은 DB에 남는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE alerts SET deleted = true, updated_at = CURRENT_TIMESTAMP "
            + "WHERE receiver_id = :userId AND deleted = false", nativeQuery = true)
    int hideAllByReceiverId(@Param("userId") Long userId);
}
