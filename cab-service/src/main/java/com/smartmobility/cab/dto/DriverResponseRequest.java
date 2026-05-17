package com.smartmobility.cab.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class DriverResponseRequest {

    @NotNull(message = "Dispatch ID is required")
    private UUID dispatchId;

    @NotNull(message = "Driver ID is required")
    private Long driverId;

    @NotNull(message = "Response is required")
    private DriverResponse response;

    public enum DriverResponse {
        ACCEPT, REJECT
    }
}