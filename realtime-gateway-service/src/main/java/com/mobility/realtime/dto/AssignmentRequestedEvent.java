package com.mobility.realtime.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentRequestedEvent {

    private String eventId;

    @Builder.Default
    private String eventType = "ASSIGNMENT_REQUESTED";

    private UUID dispatchId;
    private UUID rideId;

    @NotNull
    private Long driverUserId;

    private Double pickupLatitude;
    private Double pickupLongitude;
    private String pickupLocation;
    private Instant expiresAt;
}
