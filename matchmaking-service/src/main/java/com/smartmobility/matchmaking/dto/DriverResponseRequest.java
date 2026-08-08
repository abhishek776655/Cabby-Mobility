package com.smartmobility.matchmaking.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
}