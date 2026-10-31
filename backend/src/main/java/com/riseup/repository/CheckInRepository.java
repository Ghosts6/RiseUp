package com.riseup.repository;

import com.riseup.domain.CheckInEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRepository extends JpaRepository<CheckInEntity, Integer> {
    List<CheckInEntity> findByUserIdOrderByScheduledAtDesc(Integer userId);
    List<CheckInEntity> findByStatus(String status);
    Optional<CheckInEntity> findFirstByUserIdAndStatusInOrderByScheduledAtDesc(Integer userId, List<String> statuses);
}
