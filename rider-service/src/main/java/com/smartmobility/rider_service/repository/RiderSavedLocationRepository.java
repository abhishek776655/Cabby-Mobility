package com.smartmobility.rider_service.repository;

import com.smartmobility.rider_service.entity.RiderSavedLocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RiderSavedLocationRepository extends JpaRepository<RiderSavedLocationEntity, Long> {
    List<RiderSavedLocationEntity> findByRiderId(Long riderId);
}
