package com.smartmobility.user_service.repository;

import com.smartmobility.user_service.entity.DriverEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<DriverEntity, Long> {
}