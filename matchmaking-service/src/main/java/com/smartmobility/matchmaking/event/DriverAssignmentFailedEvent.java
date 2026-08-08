package com.smartmobility.matchmaking.event;

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
public class DriverAssignmentFailedEvent {
    private String eventId;
    @Builder.Default
    private String eventType = "DRIVER_ASSIGNMENT_FAILED";
    private UUID dispatchId;
    private UUID rideId;
    private Long driverUserId;
    private String reason;
    private Instant failedAt;
}
