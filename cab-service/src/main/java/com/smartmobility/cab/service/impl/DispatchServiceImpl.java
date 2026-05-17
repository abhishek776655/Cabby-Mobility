package com.smartmobility.cab.service.impl;

import com.smartmobility.cab.client.MatchmakingServiceClient;
import com.smartmobility.cab.dto.DispatchStatusResponse;
import com.smartmobility.cab.event.DriverResponseEvent;
import com.smartmobility.cab.kafka.RideEventProducer;
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

    @Override
    public void handleDriverResponse(UUID dispatchId, Long driverUserId, boolean accepted) {
        eventProducer.publishDriverResponse(
                DriverResponseEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .dispatchId(dispatchId)
                        .driverUserId(driverUserId)
                        .accepted(accepted)
                        .responseAt(Instant.now().toString())
                        .build()
        );
    }

    @Override
    public void cancelDispatch(UUID rideId, String reason) {
        // Dispatch cancellation is currently synchronous through matchmaking status lookup only.
    }

    @Override
    public Optional<DispatchStatusResponse> getDispatchStatus(UUID rideId) {
        return Optional.ofNullable(matchmakingClient.getDispatchStatus(rideId));
    }
}
