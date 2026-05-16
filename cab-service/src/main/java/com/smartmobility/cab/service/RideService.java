package com.smartmobility.cab.service;

import com.smartmobility.cab.dto.RideRequestDTO;
import com.smartmobility.cab.dto.RideResponseDTO;

import java.util.UUID;

public interface RideService {

    RideResponseDTO createRide(RideRequestDTO request);

    RideResponseDTO getRideById(UUID rideId);

    RideResponseDTO matchRide(UUID rideId);

    void handleDriverAssignedEvent(String eventId, UUID rideId, Long driverId);

    RideResponseDTO startRide(UUID rideId);

    RideResponseDTO completeRide(UUID rideId);

    RideResponseDTO cancelRide(UUID rideId);

}
