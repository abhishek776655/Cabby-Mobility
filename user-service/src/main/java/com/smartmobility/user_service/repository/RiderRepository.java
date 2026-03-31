package com.smartmobility.user_service.repository;

import com.smartmobility.user_service.entity.RiderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiderRepository extends JpaRepository<RiderEntity, Long> {
}