package com.riseup.repository;

import com.riseup.domain.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, Integer> {}
