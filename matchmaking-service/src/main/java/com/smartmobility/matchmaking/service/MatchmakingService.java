package com.smartmobility.matchmaking.service;

import com.smartmobility.matchmaking.event.RideRequestedEvent;

public interface MatchmakingService {
    void matchRide(RideRequestedEvent event);
}