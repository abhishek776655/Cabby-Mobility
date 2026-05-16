package com.smartmobility.cab.event;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverAssignedEvent {

    private String eventId;   // for reliability
    private UUID rideId;
    private Long driverId;
}
