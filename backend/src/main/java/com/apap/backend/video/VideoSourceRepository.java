package com.apap.backend.video;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoSourceRepository extends JpaRepository<VideoSource, Long> {
    List<VideoSource> findAllByUserIdOrderByIdDesc(Long userId);
}
