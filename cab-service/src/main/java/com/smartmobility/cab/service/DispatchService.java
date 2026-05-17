package com.smartmobility.cab.service;

import com.smartmobility.cab.dto.DispatchStatusResponse;

import java.util.Optional;
import java.util.UUID;

public interface DispatchService {

    void handleDriverResponse(UUID dispatchId, Long driverId, boolean accepted);

    void cancelDispatch(UUID rideId, String reason);

    Optional<DispatchStatusResponse> getDispatchStatus(UUID rideId);
}