package com.riseup.repository;

import com.riseup.domain.AlarmLogEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmLogRepository extends JpaRepository<AlarmLogEntity, Integer> {
    List<AlarmLogEntity> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(Integer userId, LocalDateTime after);
    List<AlarmLogEntity> findByUserIdOrderByCreatedAtDesc(Integer userId);
}
