package com.smartmobility.pricing.repository;

import com.smartmobility.pricing.entity.FareEstimateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FareEstimateRepository extends JpaRepository<FareEstimateEntity, UUID> {
    Optional<FareEstimateEntity> findByRideId(String rideId);
    Optional<FareEstimateEntity> findByIdempotencyKey(String idempotencyKey);
}
