package com.smartmobility.rider_service.repository;

import com.smartmobility.rider_service.entity.RiderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RiderRepository extends JpaRepository<RiderEntity, Long> {
    Optional<RiderEntity> findByUserId(Long userId);
}
