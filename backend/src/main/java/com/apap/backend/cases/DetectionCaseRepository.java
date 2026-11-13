package com.apap.backend.cases;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetectionCaseRepository extends JpaRepository<DetectionCase, Long> {
    boolean existsByName(String name);

    List<DetectionCase> findAllByOrderByIdAsc();
}
