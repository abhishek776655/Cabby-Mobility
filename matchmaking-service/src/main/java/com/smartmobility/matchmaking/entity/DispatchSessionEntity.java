package com.smartmobility.matchmaking.entity;

import com.smartmobility.matchmaking.domain.DispatchStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispatch_sessions")
public class DispatchSessionEntity {

    @Id
    @Column(name = "dispatch_id")
    private UUID dispatchId;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "rider_id", nullable = false)
    private Long riderUserId;

    @Column(name = "pickup_latitude")
    private Double pickupLatitude;

    @Column(name = "pickup_longitude")
    private Double pickupLongitude;

    @Column(name = "pickup_location")
    private String pickupLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DispatchStatus status;

    @Column(name = "current_driver_id")
    private Long currentDriverUserId;

    @Column(name = "remaining_candidates", columnDefinition = "TEXT")
    private String remainingCandidates;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    // Index into MatchmakingProperties.discovery.radiusStepsKm — which search
    // radius tier this session is currently (or about to be) searching at.
    @Column(name = "radius_sweep_index")
    private Integer radiusSweepIndex = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getDispatchId() { return dispatchId; }
    public void setDispatchId(UUID dispatchId) { this.dispatchId = dispatchId; }
    public UUID getRideId() { return rideId; }
    public void setRideId(UUID rideId) { this.rideId = rideId; }
    public Long getRiderUserId() { return riderUserId; }
    public void setRiderUserId(Long riderUserId) { this.riderUserId = riderUserId; }
    public Double getPickupLatitude() { return pickupLatitude; }
    public void setPickupLatitude(Double pickupLatitude) { this.pickupLatitude = pickupLatitude; }
    public Double getPickupLongitude() { return pickupLongitude; }
    public void setPickupLongitude(Double pickupLongitude) { this.pickupLongitude = pickupLongitude; }
    public String getPickupLocation() { return pickupLocation; }
    public void setPickupLocation(String pickupLocation) { this.pickupLocation = pickupLocation; }
    public DispatchStatus getStatus() { return status; }
    public void setStatus(DispatchStatus status) { this.status = status; }
    public Long getCurrentDriverUserId() { return currentDriverUserId; }
    public void setCurrentDriverUserId(Long currentDriverUserId) { this.currentDriverUserId = currentDriverUserId; }
    public String getRemainingCandidates() { return remainingCandidates; }
    public void setRemainingCandidates(String remainingCandidates) { this.remainingCandidates = remainingCandidates; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getRadiusSweepIndex() { return radiusSweepIndex; }
    public void setRadiusSweepIndex(Integer radiusSweepIndex) { this.radiusSweepIndex = radiusSweepIndex; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
