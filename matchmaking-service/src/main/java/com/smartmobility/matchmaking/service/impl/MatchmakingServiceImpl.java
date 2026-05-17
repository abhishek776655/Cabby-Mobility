package com.smartmobility.matchmaking.service.impl;

import com.smartmobility.matchmaking.client.DriverServiceClient;
import com.smartmobility.matchmaking.client.LocationServiceClient;
import com.smartmobility.matchmaking.dto.DriverResponseDTO;
import com.smartmobility.matchmaking.entity.ProcessedEvent;
import com.smartmobility.matchmaking.event.DriverAssignedEvent;
import com.smartmobility.matchmaking.event.MatchmakingFailedEvent;
import com.smartmobility.matchmaking.event.RideRequestedEvent;
import com.smartmobility.matchmaking.repository.ProcessedEventRepository;
import com.smartmobility.matchmaking.scoring.DriverScoringStrategy;
import com.smartmobility.matchmaking.kafka.MatchmakingEventProducer;
import com.smartmobility.matchmaking.service.MatchmakingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchmakingServiceImpl implements MatchmakingService {

    private final LocationServiceClient locationClient;
    private final DriverServiceClient driverClient;
    private final MatchmakingEventProducer eventProducer;
    private final ProcessedEventRepository processedEventRepository;
    private final DriverScoringStrategy scoringStrategy;

    @Value("${matchmaking.default-radius-km:5}")
    private double defaultRadiusKm;

    @Value("${matchmaking.default-limit:10}")
    private int defaultLimit;

    @Override
    public void matchRide(RideRequestedEvent event) {
        if (processedEventRepository.existsById(event.getEventId())) {
            return;
        }

        List<Long> candidateIds = locationClient.findNearbyDrivers(
                event.getPickupLatitude(),
                event.getPickupLongitude(),
                defaultRadiusKm,
                defaultLimit
        );

        List<ScoredDriver> candidates = new ArrayList<>();
        DriverResponseDTO selectedDriver = null;
        double selectedScore = -1;

        for (Long candidateId : candidateIds) {
            DriverResponseDTO driver = driverClient.getDriver(candidateId);
            if (driver == null) {
                continue;
            }

            if (!Boolean.TRUE.equals(driver.getAvailable())) {
                continue;
            }

            double score = scoringStrategy.calculateScore(driver, event);

            candidates.add(new ScoredDriver(driver, score));

            if (selectedDriver == null || score > selectedScore) {
                selectedDriver = driver;
                selectedScore = score;
            }
        }

        if (selectedDriver == null) {
            saveFailureAndPublish(event, "NO_DRIVER_AVAILABLE");
            saveProcessed(event);
            return;
        }

        boolean assigned = assignDriver(event, selectedDriver, candidates);
        if (!assigned && !candidates.isEmpty()) {
            for (ScoredDriver candidate : candidates) {
                if (candidate.driver.getUserId().equals(selectedDriver.getUserId())) {
                    continue;
                }
                selectedDriver = candidate.driver;
                selectedScore = candidate.score;
                assigned = assignDriver(event, selectedDriver, candidates);
                if (assigned) {
                    break;
                }
            }
        }

        if (!assigned) {
            saveFailureAndPublish(event, "NO_DRIVER_AVAILABLE");
        }

        saveProcessed(event);
    }

    private boolean assignDriver(RideRequestedEvent event, DriverResponseDTO driver, List<ScoredDriver> candidates) {
        try {
            driverClient.markUnavailable(driver.getUserId());

            DriverAssignedEvent assignedEvent = DriverAssignedEvent.builder()
                    .eventId(event.getEventId() + "-assigned")
                    .rideId(event.getRideId())
                    .driverId(driver.getUserId())
                    .assignedAt(LocalDateTime.now())
                    .build();

            eventProducer.publishDriverAssigned(assignedEvent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void saveFailureAndPublish(RideRequestedEvent event, String reason) {
        MatchmakingFailedEvent failedEvent = MatchmakingFailedEvent.builder()
                .eventId(event.getEventId() + "-failed")
                .rideId(event.getRideId())
                .reason(reason)
                .failedAt(LocalDateTime.now())
                .build();

        eventProducer.publishMatchmakingFailed(failedEvent);
    }

    private void saveProcessed(RideRequestedEvent event) {
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("ride-requested")
                .build();

        processedEventRepository.save(processedEvent);
    }

    private record ScoredDriver(DriverResponseDTO driver, double score) {}
}