package com.riseup.repository;

import com.riseup.domain.AlarmEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmRepository extends JpaRepository<AlarmEntity, Integer> {
    List<AlarmEntity> findByUserIdOrderByTimeAsc(Integer userId);
    long countByUserId(Integer userId);
    List<AlarmEntity> findByActiveTrue();
    Optional<AlarmEntity> findByIdAndUserId(Integer id, Integer userId);
}
