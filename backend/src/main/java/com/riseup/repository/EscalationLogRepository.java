package com.riseup.repository;

import com.riseup.domain.EscalationLogEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EscalationLogRepository extends JpaRepository<EscalationLogEntity, Integer> {
    List<EscalationLogEntity> findByUserIdOrderByCreatedAtDesc(Integer userId);
    List<EscalationLogEntity> findByEpisodeOrderByAttemptAsc(String episode);
    List<EscalationLogEntity> findByAlarmIdOrderByCreatedAtAsc(Integer alarmId);
    Optional<EscalationLogEntity> findByIdAndUserId(Integer id, Integer userId);
}
