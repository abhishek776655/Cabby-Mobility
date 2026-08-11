package com.smartmobility.cab.service.impl;

import com.smartmobility.cab.dto.RideRequestDTO;
import com.smartmobility.cab.dto.RideResponseDTO;
import com.smartmobility.cab.entity.ProcessedEvent;
import com.smartmobility.cab.entity.RideEntity;
import com.smartmobility.cab.event.RideCancelledEvent;
import com.smartmobility.cab.event.RideCompletedEvent;
import com.smartmobility.cab.event.RideRequestedEvent;
import com.smartmobility.cab.exception.RideNotFoundException;
import com.smartmobility.cab.client.PricingServiceClient;
import com.smartmobility.cab.mapper.RideMapper;
import com.smartmobility.cab.kafka.RideEventProducer;
import com.smartmobility.cab.repository.ProcessedEventRepository;
import com.smartmobility.cab.repository.RideRepository;
import com.smartmobility.cab.security.RideAuthorizationGuard;
import com.smartmobility.cab.service.RideService;
import com.smartmobility.cab.state.RideState;
import com.smartmobility.cab.state.RideStateFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class RideServiceImpl implements RideService {

    private final RideRepository rideRepository;
    private final RideStateFactory rideStateFactory;
    private final RideEventProducer producer;
    private final ProcessedEventRepository processedEventRepository;
    private final RideAuthorizationGuard authorizationGuard;
    private final PricingServiceClient pricingServiceClient;
    
    // Business Metrics
    private final MeterRegistry meterRegistry;
    private final Counter ridesRequestedCounter;
    private final Counter ridesCompletedCounter;
    private final Counter driverAllocatedCounter;
    private final Timer matchmakingLatencyTimer;
    private final DistributionSummary fareDistribution;

    public RideServiceImpl(RideRepository rideRepository, RideStateFactory rideStateFactory,
                           RideEventProducer producer, ProcessedEventRepository processedEventRepository,
                           RideAuthorizationGuard authorizationGuard, PricingServiceClient pricingServiceClient,
                           MeterRegistry meterRegistry) {
        this.rideRepository = rideRepository;
        this.rideStateFactory = rideStateFactory;
        this.producer = producer;
        this.processedEventRepository = processedEventRepository;
        this.authorizationGuard = authorizationGuard;
        this.pricingServiceClient = pricingServiceClient;

        this.meterRegistry = meterRegistry;

        Gauge.builder("business.rides.active", rideRepository, 
            repo -> repo.countByStatusIn(java.util.List.of(
                    com.smartmobility.cab.entity.RideStatus.REQUESTED, 
                    com.smartmobility.cab.entity.RideStatus.DRIVER_ASSIGNED,
                    com.smartmobility.cab.entity.RideStatus.ONGOING)))
            .description("Number of rides currently in progress")
            .register(meterRegistry);

        Gauge.builder("business.riders.active_in_ride", rideRepository,
            repo -> repo.countDistinctRiderUserIdByStatusIn(java.util.List.of(
                    com.smartmobility.cab.entity.RideStatus.REQUESTED,
                    com.smartmobility.cab.entity.RideStatus.DRIVER_ASSIGNED,
                    com.smartmobility.cab.entity.RideStatus.ONGOING)))
            .description("Distinct riders currently in an active (non-terminal) ride")
            .register(meterRegistry);

        // "Active" here = requested a ride within the last 15 minutes; riders have no
        // online/offline presence concept like drivers, so recent-activity is the closest
        // proxy for "currently using the app".
        Gauge.builder("business.riders.active", rideRepository,
            repo -> repo.countDistinctRiderUserIdSince(LocalDateTime.now().minusMinutes(15)))
            .description("Distinct riders who requested a ride in the last 15 minutes")
            .register(meterRegistry);

        this.matchmakingLatencyTimer = Timer.builder("business.rides.matchmaking.latency")
            .description("Time taken to match a rider with a driver")
            .register(meterRegistry);

        this.fareDistribution = DistributionSummary.builder("business.rides.fare")
            .description("Distribution of fare values for completed rides")
            .register(meterRegistry);

        this.ridesRequestedCounter = Counter.builder("business.rides.requested")
                .description("Number of rides requested by riders")
                .register(meterRegistry);
        this.ridesCompletedCounter = Counter.builder("business.rides.completed")
                .description("Number of rides successfully completed")
                .register(meterRegistry);
        this.driverAllocatedCounter = Counter.builder("business.rides.driver_allocated")
                .description("Number of drivers successfully allocated to rides")
                .register(meterRegistry);
    }

    private RideEntity getRide(UUID id) {
        return rideRepository.findById(id)
                .orElseThrow(() -> new RideNotFoundException("Ride not found"));
    }

    @Override
    @Transactional
    public RideResponseDTO createRide(RideRequestDTO request, Long currentUserId) {
        authorizationGuard.assertRiderOwnsRide(
                RideEntity.builder().riderUserId(request.getRiderUserId()).build(),
                currentUserId
        );

        // 1. Convert DTO → Entity
        RideEntity ride = RideMapper.toEntity(request);

        // 2. Fetch Pricing Quote
        try {
            pricingServiceClient.quote(
                    ride.getPickupLatitude(), ride.getPickupLongitude(),
                    ride.getDropLatitude(), ride.getDropLongitude(),
                    ride.getVehicleType()
            ).ifPresent(quoteData -> {
                ride.setFareEstimateId(quoteData.estimateId());
                ride.setFare((double) quoteData.breakdown().total()); // Base fare estimate
            });
        } catch (Exception e) {
            log.warn("Failed to get pricing quote for ride, falling back to null fare", e);
        }

        // 3. Save to DB
        RideEntity savedRide = rideRepository.save(ride);

        // 4. Convert Entity → Response DTO

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
        
        ridesRequestedCounter.increment();
        return RideMapper.toResponseDTO(savedRide);    }

    @Override
    public RideResponseDTO getRideById(UUID rideId, Long currentUserId) {
        // 1. Fetch ride from DB
        RideEntity ride = getRide(rideId);
        authorizationGuard.assertRideOwnedByRiderOrDriver(ride, currentUserId);
        // 2. Convert to DTO
        return RideMapper.toResponseDTO(ride);
    }

    @Override
    public RideResponseDTO cancelRide(UUID rideId, Long currentUserId) {
        // 1. Fetch ride
        RideEntity ride = getRide(rideId);
        authorizationGuard.assertRiderOwnsRide(ride, currentUserId);

        // 2. Get current state
        RideState state = rideStateFactory.getState(ride.getStatus());

        // 3. Perform state transition
        state.cancel(ride);

        // 4. Save updated ride
        RideEntity updatedRide = rideRepository.save(ride);

        // 5. Tell matchmaking to release the driver's reservation, if one was assigned —
        // otherwise they'd stay falsely reserved until the on-trip safety-net TTL expires.
        if (updatedRide.getDriverUserId() != null) {
            producer.publishRideCancelled(RideCancelledEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .rideId(updatedRide.getId())
                    .driverUserId(updatedRide.getDriverUserId())
                    .riderUserId(updatedRide.getRiderUserId())
                    .cancelledAt(LocalDateTime.now().toString())
                    .build());
        }

        // 6. Return response
        meterRegistry.counter("business.rides.failed", "reason", "USER_CANCELLED").increment();
        return RideMapper.toResponseDTO(updatedRide);
    }

    @Override
    public RideResponseDTO startRide(UUID rideId, Long currentUserId) {
        // 1. Fetch ride
        RideEntity ride = getRide(rideId);
        authorizationGuard.assertAssignedDriverOwnsRide(ride, currentUserId);

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
    public RideResponseDTO completeRide(UUID rideId, Long currentUserId) {
        // 1. Fetch ride
        RideEntity ride = getRide(rideId);
        authorizationGuard.assertAssignedDriverOwnsRide(ride, currentUserId);

        // 2. Get current state
        RideState state = rideStateFactory.getState(ride.getStatus());

        // 3. Move to next state
        state.complete(ride);

        // 4. Fetch Pricing Finalize
        if (ride.getFareEstimateId() != null) {
            try {
                pricingServiceClient.finalizeFare(ride.getId().toString(), ride.getFareEstimateId())
                        .ifPresent(finalizeData -> {
                            ride.setFare((double) finalizeData.finalBreakdown().total());
                        });
            } catch (Exception e) {
                log.warn("Failed to finalize pricing for ride {}", rideId, e);
            }
        }

        // 5. Save updated ride
        RideEntity updatedRide = rideRepository.save(ride);

        // 6. Tell matchmaking the driver is free again — this is what actually releases
        // their reservation now that acceptance no longer does (see handleAcceptance).
        producer.publishRideCompleted(RideCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .rideId(updatedRide.getId())
                .driverUserId(updatedRide.getDriverUserId())
                .riderUserId(updatedRide.getRiderUserId())
                .completedAt(LocalDateTime.now().toString())
                .fare(updatedRide.getFare())
                .build());

        // 6. Return response
        if (updatedRide.getFare() != null) {
            fareDistribution.record(updatedRide.getFare());
        }
        ridesCompletedCounter.increment();
        return RideMapper.toResponseDTO(updatedRide);
    }

    @Override
    @Transactional
    public RideResponseDTO retryMatch(UUID rideId, Long currentUserId) {
        // 1. Fetch ride
        RideEntity ride = getRide(rideId);
        authorizationGuard.assertRiderOwnsRide(ride, currentUserId);

        // 2. Get current state (throws InvalidStateTransitionException unless
        // ride is NO_DRIVER_AVAILABLE — see RideState.retryMatch impls)
        RideState state = rideStateFactory.getState(ride.getStatus());
        state.retryMatch(ride);

        // 3. Save updated ride
        RideEntity updatedRide = rideRepository.save(ride);

        // 4. Re-publish ride-requested so matchmaking starts a fresh dispatch
        // for this SAME rideId (matchmaking resets its existing FAILED session
        // in place rather than creating a duplicate one for the same ride).
        producer.publishRideRequested(
                RideRequestedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .rideId(updatedRide.getId())
                        .riderUserId(updatedRide.getRiderUserId())
                        .pickupLocation(updatedRide.getPickupLocation())
                        .dropLocation(updatedRide.getDropLocation())
                        .pickupLatitude(updatedRide.getPickupLatitude())
                        .pickupLongitude(updatedRide.getPickupLongitude())
                        .dropLatitude(updatedRide.getDropLatitude())
                        .dropLongitude(updatedRide.getDropLongitude())
                        .build()
        );

        ridesRequestedCounter.increment();
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
        
        if (ride.getCreatedAt() != null) {
            matchmakingLatencyTimer.record(Duration.between(ride.getCreatedAt(), LocalDateTime.now()));
        }
        driverAllocatedCounter.increment();
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
        
        meterRegistry.counter("business.rides.failed", "reason", "NO_DRIVERS_AVAILABLE").increment();
    }
}
