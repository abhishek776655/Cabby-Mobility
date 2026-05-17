package com.smartmobility.cab.event;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverResponseEvent {

    private String eventId;
    private UUID dispatchId;
    private UUID rideId;
    private Long driverUserId;
    private boolean accepted;
    private String responseAt;
}