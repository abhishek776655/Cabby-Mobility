package com.smartmobility.cab.event;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchmakingFailedEvent {

    private String eventId;
    private UUID rideId;
    private String reason;
    private String failedAt;
}