package com.smartmobility.matchmaking.service;

import com.smartmobility.matchmaking.dto.DispatchStatusResponse;
import com.smartmobility.matchmaking.event.RideRequestedEvent;

import java.util.Optional;
import java.util.UUID;

public interface DispatchService {

    void startDispatch(RideRequestedEvent event);

    void handleDriverResponse(UUID dispatchId, Long driverUserId, boolean accepted);

    void cancelDispatch(UUID rideId, String reason);

    Optional<DispatchStatusResponse> getDispatchStatus(UUID rideId);
}