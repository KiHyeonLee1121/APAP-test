package com.apap.backend.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {
    List<AnalysisJob> findAllByVideoSourceUserIdOrderByIdDesc(Long userId);
}
