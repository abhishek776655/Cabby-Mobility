package com.smartmobility.notification_service.event;

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
    private UUID rideId;
    private Long driverUserId;
    private Long riderUserId;
    private String completedAt;
}
