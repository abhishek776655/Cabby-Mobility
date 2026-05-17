package com.smartmobility.matchmaking.kafka;

import com.smartmobility.matchmaking.event.DriverAssignedEvent;
import com.smartmobility.matchmaking.event.MatchmakingFailedEvent;

public interface MatchmakingEventProducer {
    void publishDriverAssigned(DriverAssignedEvent event);
    void publishMatchmakingFailed(MatchmakingFailedEvent event);
}