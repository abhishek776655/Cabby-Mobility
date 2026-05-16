package com.smartmobility.driver_service.repository;

import com.smartmobility.driver_service.entity.DriverEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DriverRepository extends JpaRepository<DriverEntity, Long> {

    Optional<DriverEntity> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
