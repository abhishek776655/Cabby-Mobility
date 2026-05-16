package com.smartmobility.matchmaking.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class CancelDispatchRequest {

    @NotNull
    private UUID rideId;

    private String reason;

    public UUID getRideId() { return rideId; }
    public void setRideId(UUID rideId) { this.rideId = rideId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}