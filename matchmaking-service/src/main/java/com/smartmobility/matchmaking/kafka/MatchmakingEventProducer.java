package com.smartmobility.matchmaking.kafka;

import com.smartmobility.matchmaking.event.AssignmentRequestedEvent;
import com.smartmobility.matchmaking.event.DriverAssignedEvent;
import com.smartmobility.matchmaking.event.MatchmakingFailedEvent;

public interface MatchmakingEventProducer {
    void publishAssignmentRequested(AssignmentRequestedEvent event);
    void publishDriverAssigned(DriverAssignedEvent event);
    void publishMatchmakingFailed(MatchmakingFailedEvent event);
}
