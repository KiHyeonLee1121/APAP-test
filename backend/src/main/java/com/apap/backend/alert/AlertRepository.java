package com.apap.backend.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findAllByReceiverIdOrderByIdDesc(Long receiverId);

    long countByReceiverIdAndStatusNot(Long receiverId, AlertStatus status);
}
