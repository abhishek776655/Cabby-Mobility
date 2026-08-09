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
public class AssignmentRequestedEvent {
    private String eventId;
    private UUID dispatchId;
    private UUID rideId;
    private Long driverUserId;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private String pickupLocation;
    private String expiresAt;
}
