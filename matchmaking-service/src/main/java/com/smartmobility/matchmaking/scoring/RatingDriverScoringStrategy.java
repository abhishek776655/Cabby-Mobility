package com.smartmobility.matchmaking.scoring;

import com.smartmobility.matchmaking.dto.DriverResponseDTO;
import com.smartmobility.matchmaking.event.RideRequestedEvent;
import org.springframework.stereotype.Component;

@Component
public class RatingDriverScoringStrategy implements DriverScoringStrategy {

    @Override
    public double calculateScore(DriverResponseDTO driver, RideRequestedEvent ride) {
        return driver.getRating() == null ? 0.0 : driver.getRating();
    }
}