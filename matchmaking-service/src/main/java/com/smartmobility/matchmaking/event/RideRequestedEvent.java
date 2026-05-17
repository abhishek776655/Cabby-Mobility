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
public class RideRequestedEvent {
    private String eventId;
    private UUID rideId;
    private Long riderUserId;
    private String pickupLocation;
    private String dropLocation;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double dropLatitude;
    private Double dropLongitude;
}
