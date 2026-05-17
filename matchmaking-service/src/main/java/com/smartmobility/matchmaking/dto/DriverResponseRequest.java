package com.smartmobility.matchmaking.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class DriverResponseRequest {

    @NotNull
    private UUID dispatchId;

    @NotNull
    private Long driverUserId;

    @NotNull
    private DriverResponse response;

    public enum DriverResponse {
        ACCEPT, REJECT
    }

    public UUID getDispatchId() { return dispatchId; }
    public void setDispatchId(UUID dispatchId) { this.dispatchId = dispatchId; }
    public Long getDriverUserId() { return driverUserId; }
    public void setDriverUserId(Long driverUserId) { this.driverUserId = driverUserId; }
    public DriverResponse getResponse() { return response; }
    public void setResponse(DriverResponse response) { this.response = response; }
}