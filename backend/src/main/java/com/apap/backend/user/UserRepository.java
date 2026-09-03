package com.apap.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByGoogleSub(String googleSub);

    Optional<User> findByEmail(String email);

    // 탈퇴(soft delete)한 계정을 제외하고 조회한다.
    Optional<User> findByIdAndDeletedFalse(Long id);

    Optional<User> findByGoogleSubAndDeletedFalse(String googleSub);

    Optional<User> findByEmailAndDeletedFalse(String email);

    boolean existsByIdAndDeletedFalse(Long id);
}
