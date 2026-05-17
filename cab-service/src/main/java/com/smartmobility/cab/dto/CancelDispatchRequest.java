package com.smartmobility.cab.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CancelDispatchRequest {

    @NotNull(message = "Ride ID is required")
    private UUID rideId;

    private String reason;
}