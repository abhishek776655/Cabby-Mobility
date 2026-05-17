package com.mobility.realtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverLocationUpdatedEvent {

    @NotBlank
    private String driverUserId;

    @NotBlank
    private String rideId;

    @NotNull
    private Double latitude;

    @NotNull
    private Double longitude;

    private Double speed;
    private Double heading;

    @NotNull
    private Instant timestamp;
}
