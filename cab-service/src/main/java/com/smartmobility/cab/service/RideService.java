package com.smartmobility.cab.service;

import com.smartmobility.cab.dto.RideRequestDTO;
import com.smartmobility.cab.dto.RideResponseDTO;

import java.util.UUID;

public interface RideService {

    RideResponseDTO createRide(RideRequestDTO request, Long currentUserId);

    RideResponseDTO getRideById(UUID rideId, Long currentUserId);

    RideResponseDTO matchRide(UUID rideId);

    void handleDriverAssignedEvent(String eventId, UUID rideId, Long driverUserId);

    void handleMatchmakingFailedEvent(String eventId, UUID rideId, String reason);

    RideResponseDTO startRide(UUID rideId, Long currentUserId);

    RideResponseDTO completeRide(UUID rideId, Long currentUserId);

    RideResponseDTO cancelRide(UUID rideId, Long currentUserId);

    // Rider-initiated "search again" on the same ride — only valid from
    // NO_DRIVER_AVAILABLE. Re-publishes ride-requested so matchmaking restarts
    // discovery for this same rideId instead of the rider creating a new ride.
    RideResponseDTO retryMatch(UUID rideId, Long currentUserId);

}
