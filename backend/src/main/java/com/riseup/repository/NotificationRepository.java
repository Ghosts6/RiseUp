package com.riseup.repository;

import com.riseup.domain.NotificationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Integer> {
    List<NotificationEntity> findByUserIdOrderByCreatedAtDesc(Integer userId);
    List<NotificationEntity> findByUserIdAndReadFalseOrderByCreatedAtDesc(Integer userId);
}
