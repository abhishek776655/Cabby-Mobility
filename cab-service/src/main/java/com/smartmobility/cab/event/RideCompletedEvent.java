package com.smartmobility.cab.event;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideCompletedEvent {

    private String eventId;
    private UUID rideId;
    private Long driverUserId;
    private Long riderUserId;
    private String completedAt;
}
