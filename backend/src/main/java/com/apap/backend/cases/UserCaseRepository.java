package com.apap.backend.cases;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCaseRepository extends JpaRepository<UserCase, Long> {
    List<UserCase> findAllByUserIdOrderByIdDesc(Long userId);

    boolean existsByUserIdAndDetectionCaseId(Long userId, Long caseId);

    Optional<UserCase> findFirstByUserIdAndActiveIsTrueOrderByIdAsc(Long userId);
}
