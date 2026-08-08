package com.smartmobility.cab.service.impl;

import com.smartmobility.cab.client.MatchmakingServiceClient;
import com.smartmobility.cab.dto.DispatchStatusResponse;
import com.smartmobility.cab.entity.RideEntity;
import com.smartmobility.cab.event.DriverResponseEvent;
import com.smartmobility.cab.exception.RideNotFoundException;
import com.smartmobility.cab.kafka.RideEventProducer;
import com.smartmobility.cab.repository.RideRepository;
import com.smartmobility.cab.security.RideAuthorizationGuard;
import com.smartmobility.cab.service.DispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DispatchServiceImpl implements DispatchService {

    private final RideEventProducer eventProducer;
    private final MatchmakingServiceClient matchmakingClient;
    private final RideRepository rideRepository;
    private final RideAuthorizationGuard authorizationGuard;

    @Override
    public void handleDriverResponse(UUID dispatchId, Long driverUserId, boolean accepted, UUID rideId, Long currentUserId) {
        authorizationGuard.assertCurrentDriverMatchesRequest(driverUserId, currentUserId);
        RideEntity ride = getRide(rideId);

        eventProducer.publishDriverResponse(
                DriverResponseEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .dispatchId(dispatchId)
                        .rideId(rideId)
                        .driverUserId(driverUserId)
                        .accepted(accepted)
                        .responseAt(Instant.now().toString())
                        .build()
        );
    }

    @Override
    public void cancelDispatch(UUID rideId, String reason, Long currentUserId) {
        RideEntity ride = getRide(rideId);
        authorizationGuard.assertRiderOwnsRide(ride, currentUserId);

        // Dispatch cancellation is currently synchronous through matchmaking status lookup only.
    }

    @Override
    public Optional<DispatchStatusResponse> getDispatchStatus(UUID rideId, Long currentUserId) {
        RideEntity ride = getRide(rideId);
        authorizationGuard.assertRideOwnedByRiderOrDriver(ride, currentUserId);
        return Optional.ofNullable(matchmakingClient.getDispatchStatus(rideId));
    }

    private RideEntity getRide(UUID rideId) {
        return rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found"));
    }
}
