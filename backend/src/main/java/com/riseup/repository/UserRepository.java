package com.riseup.repository;

import com.riseup.domain.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {
    Optional<UserEntity> findByApiToken(String apiToken);
    Optional<UserEntity> findFirstByOrderByIdAsc();
    Optional<UserEntity> findByPhone(String phone);
}
