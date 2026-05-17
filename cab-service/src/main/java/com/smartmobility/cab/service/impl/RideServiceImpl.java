package com.smartmobility.cab.service.impl;

import com.smartmobility.cab.dto.RideRequestDTO;
import com.smartmobility.cab.dto.RideResponseDTO;
import com.smartmobility.cab.entity.ProcessedEvent;
import com.smartmobility.cab.entity.RideEntity;
import com.smartmobility.cab.event.RideRequestedEvent;
import com.smartmobility.cab.exception.RideNotFoundException;
import com.smartmobility.cab.mapper.RideMapper;
import com.smartmobility.cab.kafka.RideEventProducer;
import com.smartmobility.cab.repository.ProcessedEventRepository;
import com.smartmobility.cab.repository.RideRepository;
import com.smartmobility.cab.service.RideService;
import com.smartmobility.cab.state.RideState;
import com.smartmobility.cab.state.RideStateFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RideServiceImpl implements RideService {

    private final RideRepository rideRepository;
    private final RideStateFactory rideStateFactory;
    private final RideEventProducer producer;
    private final ProcessedEventRepository processedEventRepository;
    private RideEntity getRide(UUID id) {
        return rideRepository.findById(id)
                .orElseThrow(() -> new RideNotFoundException("Ride not found"));
    }
    @Override
    public RideResponseDTO createRide(RideRequestDTO request) {
        // 1. Convert DTO → Entity
        RideEntity ride = RideMapper.toEntity(request);

        // 2. Save to DB
        RideEntity savedRide = rideRepository.save(ride);

        // 3. Convert Entity → Response DTO

        // 4.  publish event
        producer.publishRideRequested(
                RideRequestedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .rideId(savedRide.getId())
                        .riderUserId(savedRide.getRiderUserId())
                        .pickupLocation(savedRide.getPickupLocation())
                        .dropLocation(savedRide.getDropLocation())
                        .pickupLatitude(savedRide.getPickupLatitude())
                        .pickupLongitude(savedRide.getPickupLongitude())
                        .dropLatitude(savedRide.getDropLatitude())
                        .dropLongitude(savedRide.getDropLongitude())
                        .build()
        );
        return RideMapper.toResponseDTO(savedRide);    }

    @Override
    public RideResponseDTO getRideById(UUID rideId) {
        // 1. Fetch ride from DB
        RideEntity ride = getRide(rideId);
        // 2. Convert to DTO
        return RideMapper.toResponseDTO(ride);
    }

    @Override
    public RideResponseDTO cancelRide(UUID rideId) {
        // 1. Fetch ride
        RideEntity ride = getRide(rideId);

        // 2. Get current state
        RideState state = rideStateFactory.getState(ride.getStatus());

        // 3. Perform state transition
        state.cancel(ride);

        // 4. Save updated ride
        RideEntity updatedRide = rideRepository.save(ride);

        // 5. Return response
        return RideMapper.toResponseDTO(updatedRide);
    }

    @Override
    public RideResponseDTO startRide(UUID rideId) {
        // 1. Fetch ride
        RideEntity ride = getRide(rideId);

        // 2. Get current state
        RideState state = rideStateFactory.getState(ride.getStatus());

        // 3. Move to next state
        state.start(ride);

        // 4. Save updated ride
        RideEntity updatedRide = rideRepository.save(ride);

        // 5. Return response
        return RideMapper.toResponseDTO(updatedRide);
    }

    @Override
    public RideResponseDTO completeRide(UUID rideId) {
        // 1. Fetch ride
        RideEntity ride = getRide(rideId);

        // 2. Get current state
        RideState state = rideStateFactory.getState(ride.getStatus());

        // 3. Move to next state
        state.complete(ride);

        // 4. Save updated ride
        RideEntity updatedRide = rideRepository.save(ride);

        // 5. Return response
        return RideMapper.toResponseDTO(updatedRide);
    }

    @Override
    public RideResponseDTO matchRide(UUID rideId) {
        // 1. Fetch ride
        RideEntity ride = getRide(rideId);

        // 2. Get current state
        RideState state = rideStateFactory.getState(ride.getStatus());

        // 3. Move to next state
        state.match(ride);

        // 4. Save updated ride
        RideEntity updatedRide = rideRepository.save(ride);

        // 5. Return response
        return RideMapper.toResponseDTO(updatedRide);
    }

    @Transactional
    public void handleDriverAssignedEvent(String eventId, UUID rideId, Long driverUserId) {

        // 1. Idempotency check
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        // 2. Fetch ride
        RideEntity ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found"));

        // 3. Apply state transition
        RideState state = rideStateFactory.getState(ride.getStatus());
        state.assignDriver(ride, driverUserId);

        rideRepository.save(ride);

        // 4. Save processed event
        processedEventRepository.save(
                ProcessedEvent.builder()
                        .eventId(eventId)
                        .eventType("DRIVER_ASSIGNED")
                        .processedAt(LocalDateTime.now())
                        .build()
        );
    }

    @Transactional
    public void handleMatchmakingFailedEvent(String eventId, UUID rideId, String reason) {
        log.info("Handling matchmaking failed: rideId={}, reason={}", rideId, reason);

        // 1. Idempotency check
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        // 2. Fetch ride
        RideEntity ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found"));

        // 3. Apply state transition to NO_DRIVER_AVAILABLE
        RideState state = rideStateFactory.getState(ride.getStatus());
        state.failNoDriver(ride);

        // 4. Save updated ride
        rideRepository.save(ride);

        log.warn("Ride {} marked as NO_DRIVER_AVAILABLE: {}", rideId, reason);

        // 5. Save processed event
        processedEventRepository.save(
                ProcessedEvent.builder()
                        .eventId(eventId)
                        .eventType("MATCHMAKING_FAILED")
                        .processedAt(LocalDateTime.now())
                        .build()
        );
    }
}
