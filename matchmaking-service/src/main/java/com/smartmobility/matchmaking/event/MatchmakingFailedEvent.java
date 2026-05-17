package com.smartmobility.matchmaking.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchmakingFailedEvent {
    private String eventId;
    private UUID rideId;
    private String reason;
    private LocalDateTime failedAt;
}