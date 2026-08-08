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
public class RideCancelledEvent {
    private String eventId;
    @Builder.Default
    private String eventType = "RIDE_CANCELLED";
    private UUID rideId;
    private Long driverUserId;
    private String cancelledAt;
}
