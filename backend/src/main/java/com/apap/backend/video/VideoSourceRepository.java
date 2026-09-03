package com.apap.backend.video;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VideoSourceRepository extends JpaRepository<VideoSource, Long> {
    List<VideoSource> findAllByUserIdOrderByIdDesc(Long userId);

    /**
     * 영상 리셋: 해당 사용자의 영상을 한 번에 숨긴다(deleted=true).
     * 행을 지우지 않으므로 S3/로컬 파일과 분석 이력은 그대로 남는다.
     * @SQLRestriction이 벌크 UPDATE에는 적용되지 않으므로 네이티브 쿼리로 처리한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE video_sources SET deleted = true, updated_at = CURRENT_TIMESTAMP "
            + "WHERE user_id = :userId AND deleted = false", nativeQuery = true)
    int hideAllByUserId(@Param("userId") Long userId);
}
