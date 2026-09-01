package com.apap.backend.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {
    List<AnalysisJob> findAllByVideoSourceUserIdOrderByIdDesc(Long userId);

    /**
     * 백그라운드 자동 분석용 조회.
     * videoSource/user가 LAZY라 별도 스레드(세션 밖)에서 접근하면 실패하므로 함께 로딩한다.
     */
    @Query("select j from AnalysisJob j join fetch j.videoSource v join fetch v.user where j.id = :id")
    Optional<AnalysisJob> findWithVideoAndUserById(@Param("id") Long id);
}
