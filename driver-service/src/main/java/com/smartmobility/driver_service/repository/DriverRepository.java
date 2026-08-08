package com.smartmobility.driver_service.repository;

import com.smartmobility.driver_service.entity.DriverEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface DriverRepository extends JpaRepository<DriverEntity, Long> {

    List<DriverEntity> findAllByUserIdIn(List<Long> userIds);

    Optional<DriverEntity> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
