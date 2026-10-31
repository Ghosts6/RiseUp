package com.riseup.repository;

import com.riseup.domain.ReminderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRepository extends JpaRepository<ReminderEntity, Integer> {
    List<ReminderEntity> findByUserIdOrderByDueAtAsc(Integer userId);
    Optional<ReminderEntity> findByIdAndUserId(Integer id, Integer userId);
    List<ReminderEntity> findByStatusIn(List<String> statuses);
}
