package com.riseup.repository;

import com.riseup.domain.EmergencyContactEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContactEntity, Integer> {
    List<EmergencyContactEntity> findByUserIdOrderByIdAsc(Integer userId);
    Optional<EmergencyContactEntity> findByIdAndUserId(Integer id, Integer userId);
    Optional<EmergencyContactEntity> findByConsentToken(String token);
    Optional<EmergencyContactEntity> findFirstByUserIdAndConsentStatusOrderByIdAsc(Integer userId, String status);
}
