package com.riseup.repository;

import com.riseup.domain.UserPreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferencesRepository extends JpaRepository<UserPreferencesEntity, Integer> {}
