package com.smartmobility.cab.event;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideRequestedEvent {

    private String eventId;   //  important for reliability
    private UUID rideId;
    private UUID riderId;

    private String pickupLocation;
    private String dropLocation;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double dropLatitude;
    private Double dropLongitude;
}
