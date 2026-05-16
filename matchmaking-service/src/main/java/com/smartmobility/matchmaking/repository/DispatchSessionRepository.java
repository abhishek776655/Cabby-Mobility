package com.smartmobility.matchmaking.repository;

import com.smartmobility.matchmaking.entity.DispatchSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DispatchSessionRepository extends JpaRepository<DispatchSessionEntity, UUID> {

    Optional<DispatchSessionEntity> findByRideId(UUID rideId);

    @Query("SELECT d FROM DispatchSessionEntity d WHERE d.expiresAt < :now AND d.status IN ('ASSIGNMENT_SENT', 'RETRYING')")
    List<DispatchSessionEntity> findExpiredDispatchSessions(Instant now);
}