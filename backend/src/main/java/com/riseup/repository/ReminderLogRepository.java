package com.riseup.repository;

import com.riseup.domain.ReminderLogEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderLogRepository extends JpaRepository<ReminderLogEntity, Integer> {
    List<ReminderLogEntity> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(Integer userId, LocalDateTime after);
    List<ReminderLogEntity> findByUserIdOrderByCreatedAtDesc(Integer userId);
}
