package com.riseup.repository;

import com.riseup.domain.RecommendationEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRepository extends JpaRepository<RecommendationEntity, Integer> {
    List<RecommendationEntity> findByUserIdAndStatusOrderByCreatedAtDesc(Integer userId, String status);
    Optional<RecommendationEntity> findByIdAndUserId(Integer id, Integer userId);
}
