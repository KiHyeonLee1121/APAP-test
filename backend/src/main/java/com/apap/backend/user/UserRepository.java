package com.apap.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByGoogleSub(String googleSub);

    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndDeletedFalse(Long id);

    Optional<User> findByGoogleSubAndDeletedFalse(String googleSub);

    Optional<User> findByEmailAndDeletedFalse(String email);

    boolean existsByIdAndDeletedFalse(Long id);
}
