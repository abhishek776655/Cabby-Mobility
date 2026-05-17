package com.smartmobility.matchmaking.scoring;

import com.smartmobility.matchmaking.dto.DriverResponseDTO;
import com.smartmobility.matchmaking.event.RideRequestedEvent;

public interface DriverScoringStrategy {
    double calculateScore(DriverResponseDTO driver, RideRequestedEvent ride);
}