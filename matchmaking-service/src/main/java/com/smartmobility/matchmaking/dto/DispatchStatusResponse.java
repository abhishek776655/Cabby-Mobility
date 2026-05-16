package com.smartmobility.matchmaking.dto;

import com.smartmobility.matchmaking.domain.DispatchStatus;
import java.time.Instant;
import java.util.UUID;

public class DispatchStatusResponse {

    private UUID dispatchId;
    private UUID rideId;
    private DispatchStatus status;
    private Long driverId;
    private Integer retryCount;
    private Instant createdAt;
    private Instant expiresAt;

    public UUID getDispatchId() { return dispatchId; }
    public void setDispatchId(UUID dispatchId) { this.dispatchId = dispatchId; }
    public UUID getRideId() { return rideId; }
    public void setRideId(UUID rideId) { this.rideId = rideId; }
    public DispatchStatus getStatus() { return status; }
    public void setStatus(DispatchStatus status) { this.status = status; }
    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}