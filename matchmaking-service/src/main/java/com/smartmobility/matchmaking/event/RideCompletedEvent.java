package com.smartmobility.matchmaking.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideCompletedEvent {
    private String eventId;
    @Builder.Default
    private String eventType = "RIDE_COMPLETED";
    private UUID rideId;
    private Long driverUserId;
    private Long riderUserId;
    private String completedAt;
}
