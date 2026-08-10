package com.smartmobility.matchmaking.service.impl;

import com.smartmobility.matchmaking.client.DriverServiceClient;
import com.smartmobility.matchmaking.client.LocationServiceClient;
import com.smartmobility.matchmaking.client.RoutingServiceClient;
import com.smartmobility.matchmaking.dto.DriverLocationDTO;
import com.smartmobility.matchmaking.dto.MatrixRequest;
import com.smartmobility.matchmaking.config.MatchmakingProperties;
import com.smartmobility.matchmaking.domain.AttemptStatus;
import com.smartmobility.matchmaking.domain.DispatchStatus;
import com.smartmobility.matchmaking.dto.DispatchStatusResponse;
import com.smartmobility.matchmaking.entity.AssignmentAttempt;
import com.smartmobility.matchmaking.entity.AssignmentStatus;
import com.smartmobility.matchmaking.entity.DispatchSessionEntity;
import com.smartmobility.matchmaking.event.*;
import com.smartmobility.matchmaking.exception.DispatchNotFoundException;
import com.smartmobility.matchmaking.exception.InvalidDispatchStateException;
import com.smartmobility.matchmaking.exception.ReservationExpiredException;
import com.smartmobility.matchmaking.kafka.MatchmakingEventProducer;
import com.smartmobility.matchmaking.repository.AssignmentAttemptRepository;
import com.smartmobility.matchmaking.repository.DispatchSessionRepository;
import com.smartmobility.matchmaking.redis.DispatchCacheService;
import com.smartmobility.matchmaking.redis.ReservationService;
import com.smartmobility.matchmaking.service.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchServiceImpl implements DispatchService {

    private final DispatchSessionRepository dispatchRepository;
    private final AssignmentAttemptRepository attemptRepository;
    private final LocationServiceClient locationClient;
    private final DriverServiceClient driverClient;
    private final RoutingServiceClient routingClient;
    private final ReservationService reservationService;
    private final DispatchCacheService cacheService;
    private final MatchmakingEventProducer eventProducer;
    private final ObjectMapper objectMapper;
    private final MatchmakingProperties properties;
    private final com.smartmobility.matchmaking.scoring.CompositeDriverRankingService rankingService;

    @Value("${matchmaking.default-radius-km:5}")
    private double discoveryRadiusKm;

    @Value("${matchmaking.default-limit:10}")
    private int discoveryLimit;

    @Override
    @Transactional
    public void startDispatch(RideRequestedEvent event) {
        Optional<DispatchSessionEntity> existing = dispatchRepository.findByRideId(event.getRideId());
        DispatchSessionEntity session;

        if (existing.isPresent()) {
            session = existing.get();
            if (session.getStatus() != DispatchStatus.FAILED && session.getStatus() != DispatchStatus.CANCELLED) {
                log.info("Dispatch already in progress for ride {}", event.getRideId());
                return;
            }
            // Terminal session for this ride — reuse the row in place for a retry
            // ("search again") rather than inserting a second row per ride.
            log.info("Retrying dispatch for ride {} (was {})", event.getRideId(), session.getStatus());
            session.setRadiusSweepIndex(0);
            session.setRetryCount(0);
            session.setCurrentDriverUserId(null);
            session.setRemainingCandidates(null);
        } else {
            session = new DispatchSessionEntity();
            session.setDispatchId(UUID.randomUUID());
            session.setRideId(event.getRideId());
            session.setCreatedAt(Instant.now());
        }

        session.setRiderUserId(event.getRiderUserId());
        session.setPickupLatitude(event.getPickupLatitude());
        session.setPickupLongitude(event.getPickupLongitude());
        session.setPickupLocation(event.getPickupLocation());
        session.setStatus(DispatchStatus.SEARCHING);
        session.setExpiresAt(Instant.now().plus(properties.getDispatchTimeoutSeconds(), ChronoUnit.SECONDS));
        session.setUpdatedAt(Instant.now());
        dispatchRepository.save(session);

        searchAndDispatch(session);
    }

    /**
     * Discover, filter, and rank drivers at the session's current radius tier.
     * On success, assigns the top candidate; on zero candidates, escalates to
     * the next wider radius (or fails if none remain) via {@link #widenOrFail}.
     */
    private void searchAndDispatch(DispatchSessionEntity session) {
        double radiusKm = currentRadiusKm(session.getRadiusSweepIndex());

        List<Long> nearbyDriverUserIds = locationClient.findNearbyDrivers(
            session.getPickupLatitude(), session.getPickupLongitude(), radiusKm, discoveryLimit);

        log.info("Nearby drivers for ride {} at radius {}km (tier {}): {}",
            session.getRideId(), radiusKm, session.getRadiusSweepIndex(), nearbyDriverUserIds);

        List<Long> eligibleDriverUserIds = filterEligibleDrivers(nearbyDriverUserIds);
        List<Long> rankedDriverUserIds = rankDrivers(eligibleDriverUserIds, session.getPickupLatitude(), session.getPickupLongitude());

        if (rankedDriverUserIds.isEmpty()) {
            widenOrFail(session);
            return;
        }

        try {
            session.setRemainingCandidates(objectMapper.writeValueAsString(rankedDriverUserIds));
        } catch (Exception e) {
            log.error("Failed to serialize candidates", e);
        }
        session.setStatus(DispatchStatus.SEARCHING);
        session.setUpdatedAt(Instant.now());
        dispatchRepository.save(session);

        cacheService.saveDispatchState(session.getDispatchId().toString(),
            DispatchStatus.SEARCHING.name(), null, session.getExpiresAt().toEpochMilli());

        assignNextCandidate(session);
    }

    /**
     * Called when the current radius tier has no viable candidates left (zero
     * found, or all exhausted via rejection/timeout). Escalates to the next
     * wider radius tier for the scheduler to retry after a short delay, or
     * gives up for good once every tier has been tried.
     */
    private void widenOrFail(DispatchSessionEntity session) {
        List<Double> radiusSteps = properties.getDiscovery().getRadiusStepsKm();
        int nextIndex = session.getRadiusSweepIndex() + 1;

        if (!radiusSteps.isEmpty() && nextIndex < radiusSteps.size()) {
            session.setRadiusSweepIndex(nextIndex);
            // Fresh retry budget for the new tier — otherwise a session that already
            // hit dispatchMaxRetries at the old (smaller) radius would widen once and
            // then immediately re-widen on the very first rejection at the new radius.
            session.setRetryCount(0);
            session.setStatus(DispatchStatus.WIDENING_SEARCH);
            Instant nextAttemptAt = Instant.now().plus(properties.getDiscovery().getSweepDelaySeconds(), ChronoUnit.SECONDS);
            session.setExpiresAt(nextAttemptAt);
            session.setUpdatedAt(Instant.now());
            dispatchRepository.save(session);

            cacheService.saveDispatchState(session.getDispatchId().toString(),
                DispatchStatus.WIDENING_SEARCH.name(), null, nextAttemptAt.toEpochMilli());

            log.info("No drivers for ride {}, widening to radius tier {} (next attempt at {})",
                session.getRideId(), nextIndex, nextAttemptAt);
            return;
        }

        completeWithFailure(session, "NO_DRIVER_AVAILABLE");
    }

    private double currentRadiusKm(int radiusSweepIndex) {
        List<Double> steps = properties.getDiscovery().getRadiusStepsKm();
        if (steps.isEmpty()) {
            return discoveryRadiusKm; // legacy fixed default if misconfigured to an empty list
        }
        return steps.get(Math.min(radiusSweepIndex, steps.size() - 1));
    }

    /**
     * Re-run discovery at the session's (already advanced) radius tier — invoked
     * by the scheduler once a WIDENING_SEARCH session's delay has elapsed.
     */
    @Override
    @Transactional
    public void retryWiderSearch(UUID dispatchId) {
        DispatchSessionEntity session = dispatchRepository.findById(dispatchId)
            .orElseThrow(() -> new DispatchNotFoundException("Dispatch not found: " + dispatchId));

        if (session.getStatus() != DispatchStatus.WIDENING_SEARCH) {
            log.warn("retryWiderSearch called for dispatch {} but status is {}, skipping", dispatchId, session.getStatus());
            return;
        }

        searchAndDispatch(session);
    }

    @Override
    @Transactional
    public void handleDriverResponse(UUID dispatchId, Long driverUserId, boolean accepted) {
        DispatchSessionEntity session = dispatchRepository.findById(dispatchId)
            .orElseThrow(() -> new DispatchNotFoundException("Dispatch not found: " + dispatchId));

        if (!Objects.equals(session.getCurrentDriverUserId(), driverUserId)) {
            log.warn("Driver {} response for dispatch {} but current driver is {}", 
                driverUserId, dispatchId, session.getCurrentDriverUserId());
            return;
        }

        if (!reservationService.hasActiveReservation(driverUserId)) {
            throw new ReservationExpiredException();
        }

        if (accepted) {
            handleAcceptance(session, driverUserId);
        } else {
            handleRejection(session, driverUserId);
        }
    }

    @Override
    @Transactional
    public void handleDispatchTimeout(UUID dispatchId) {
        DispatchSessionEntity session = dispatchRepository.findById(dispatchId)
            .orElseThrow(() -> new DispatchNotFoundException("Dispatch not found: " + dispatchId));

        if (session.getCurrentDriverUserId() != null) {
            reservationService.releaseReservation(
                session.getCurrentDriverUserId(),
                session.getDispatchId().toString());
        }

        recordAttempt(session.getDispatchId(), session.getCurrentDriverUserId(), null, AttemptStatus.TIMEOUT, "DRIVER_TIMEOUT");

        List<Long> remaining = parseCandidates(session.getRemainingCandidates());
        if (remaining.isEmpty()) {
            widenOrFail(session);
            return;
        }

        retryWithNextCandidate(session, remaining);
    }

    private void handleAcceptance(DispatchSessionEntity session, Long driverUserId) {
        // Do NOT release the reservation here: the driver is now on-trip, not free. Extend
        // its TTL as a safety net; ride-completed/ride-cancelled explicitly releases it, this
        // just guards against that event never arriving.
        reservationService.extendReservation(
                driverUserId, session.getDispatchId().toString(), properties.getOnTripReservationSeconds());

        session.setStatus(DispatchStatus.ASSIGNED);
        session.setUpdatedAt(Instant.now());
        dispatchRepository.save(session);

        recordAttempt(session.getDispatchId(), driverUserId, null, AttemptStatus.ACCEPTED, null);
        
        cacheService.saveDispatchState(session.getDispatchId().toString(), 
            DispatchStatus.ASSIGNED.name(), driverUserId, 0);

        DriverAssignedEvent assignedEvent = DriverAssignedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .rideId(session.getRideId())
            .driverUserId(driverUserId)
            .riderUserId(session.getRiderUserId())
            .assignedAt(java.time.LocalDateTime.now())
            .build();
        
        eventProducer.publishDriverAssigned(assignedEvent);
        
        log.info("Driver {} assigned to ride {}", driverUserId, session.getRideId());
    }

    private void handleRejection(DispatchSessionEntity session, Long driverUserId) {
        reservationService.releaseReservation(driverUserId, session.getDispatchId().toString());
        
        recordAttempt(session.getDispatchId(), driverUserId, null, AttemptStatus.REJECTED, "DRIVER_REJECTED");

        List<Long> remaining = parseCandidates(session.getRemainingCandidates());

        if (remaining.isEmpty()) {
            widenOrFail(session);
        } else {
            retryWithNextCandidate(session, remaining);
        }
    }

    private void retryWithNextCandidate(DispatchSessionEntity session, List<Long> remainingDrivers) {
        if (remainingDrivers.isEmpty()) {
            widenOrFail(session);
            return;
        }

        // Bound tail latency at this radius tier: without this, a full sweep of
        // discoveryLimit (40) candidates at timeoutSeconds each could take up to
        // 40 * timeoutSeconds before escalating. Cap retries so a tier gives up
        // (and widens) reasonably fast rather than exhaustively working through
        // every candidate first.
        if (session.getRetryCount() >= properties.getDispatchMaxRetries()) {
            widenOrFail(session);
            return;
        }

        Long nextDriver = remainingDrivers.get(0);
        List<Long> nextList = remainingDrivers.size() > 1 ? remainingDrivers.subList(1, remainingDrivers.size()) : List.of();

        boolean reserved = reservationService.acquireReservation(
            nextDriver, session.getDispatchId().toString(), session.getRideId().toString());

        if (!reserved) {
            retryWithNextCandidate(session, nextList);
            return;
        }

        Instant retryExpiresAt = Instant.now().plus(properties.getDispatchTimeoutSeconds(), ChronoUnit.SECONDS);
        session.setStatus(DispatchStatus.RETRYING);
        session.setCurrentDriverUserId(nextDriver);
        session.setRetryCount(session.getRetryCount() + 1);
        session.setExpiresAt(retryExpiresAt);
        
        try {
            session.setRemainingCandidates(objectMapper.writeValueAsString(nextList));
        } catch (Exception e) {}
        
        session.setUpdatedAt(Instant.now());
        dispatchRepository.save(session);

        recordAttempt(session.getDispatchId(), nextDriver, null, AttemptStatus.RESERVED, null);

        log.info("Retry: Assignment requested to driver {} for ride {}", nextDriver, session.getRideId());
        publishAssignmentRequested(session, nextDriver);

        cacheService.saveDispatchState(session.getDispatchId().toString(),
            DispatchStatus.ASSIGNMENT_SENT.name(), nextDriver, retryExpiresAt.toEpochMilli());
    }

    private void completeWithFailure(DispatchSessionEntity session, String reason) {
        session.setStatus(DispatchStatus.FAILED);
        session.setUpdatedAt(Instant.now());
        dispatchRepository.save(session);

        MatchmakingFailedEvent failedEvent = MatchmakingFailedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .rideId(session.getRideId())
            .reason(reason)
            .riderUserId(session.getRiderUserId())
            .failedAt(java.time.LocalDateTime.now())
            .build();
        
        eventProducer.publishMatchmakingFailed(failedEvent);
        
        cacheService.deleteDispatchState(session.getDispatchId().toString());
        
        log.info("Dispatch failed for ride {}: {}", session.getRideId(), reason);
    }

    @Override
    @Transactional
    public void cancelDispatch(UUID rideId, String reason) {
        DispatchSessionEntity session = dispatchRepository.findByRideId(rideId)
            .orElseThrow(() -> new DispatchNotFoundException("No dispatch found for ride: " + rideId));

        if (session.getStatus() == DispatchStatus.ASSIGNED || session.getStatus() == DispatchStatus.FAILED) {
            throw new InvalidDispatchStateException("Cannot cancel dispatch in status: " + session.getStatus());
        }

        if (session.getCurrentDriverUserId() != null) {
            reservationService.releaseReservation(session.getCurrentDriverUserId(), session.getDispatchId().toString());
        }

        session.setStatus(DispatchStatus.CANCELLED);
        session.setUpdatedAt(Instant.now());
        dispatchRepository.save(session);

        cacheService.deleteDispatchState(session.getDispatchId().toString());
        
        log.info("Dispatch cancelled for ride {}: {}", rideId, reason);
    }

    @Override
    public Optional<DispatchStatusResponse> getDispatchStatus(UUID rideId) {
        return dispatchRepository.findByRideId(rideId)
            .map(session -> {
                DispatchStatusResponse response = new DispatchStatusResponse();
                response.setDispatchId(session.getDispatchId());
                response.setRideId(session.getRideId());
                response.setStatus(session.getStatus());
                response.setDriverUserId(session.getCurrentDriverUserId());
                response.setRetryCount(session.getRetryCount());
                response.setCreatedAt(session.getCreatedAt());
                response.setExpiresAt(session.getExpiresAt());
                return response;
            });
    }

    private List<Long> filterEligibleDrivers(List<Long> driverUserIds) {
        return driverUserIds.stream()
            .filter(driverUserId -> {
                if (reservationService.hasActiveReservation(driverUserId)) {
                    return false;
                }
                var driver = driverClient.getDriver(driverUserId);
                return driver != null && Boolean.TRUE.equals(driver.getAvailable());
            })
            .toList();
    }

    private List<Long> rankDrivers(List<Long> driverUserIds, double lat, double lng) {
        if (driverUserIds == null || driverUserIds.isEmpty()) return List.of();

        List<DriverLocationDTO> locations = locationClient.getDriverLocationsBatch(driverUserIds);
        if (locations.isEmpty()) return driverUserIds;

        List<MatrixRequest.Location> targets = locations.stream()
            .map(loc -> MatrixRequest.Location.builder().lat(loc.getLat()).lng(loc.getLng()).build())
            .toList();

        var durationsOpt = routingClient.getDurationsSeconds(lat, lng, targets);
        if (durationsOpt.isEmpty()) {
            log.warn("Routing service unavailable, falling back to unranked driver order");
            return driverUserIds;
        }
        List<Double> durations = durationsOpt.get();

        if (durations.size() < locations.size()) {
            log.warn("Routing service returned fewer durations than requested drivers, falling back to unranked driver order");
            return driverUserIds;
        }

        List<Long> rankedIds = locations.stream().map(DriverLocationDTO::getDriverUserId).toList();
        List<com.smartmobility.matchmaking.dto.DriverResponseDTO> driverDetails = driverClient.getDriversBatch(rankedIds);
        Map<Long, Double> ratingsByDriver = driverDetails.stream()
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toMap(
                com.smartmobility.matchmaking.dto.DriverResponseDTO::getUserId,
                com.smartmobility.matchmaking.dto.DriverResponseDTO::getRating));

        List<com.smartmobility.matchmaking.scoring.DriverCandidate> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < locations.size(); i++) {
            Long driverUserId = locations.get(i).getDriverUserId();
            candidates.add(new com.smartmobility.matchmaking.scoring.DriverCandidate(
                driverUserId, ratingsByDriver.get(driverUserId), durations.get(i)));
        }

        return rankingService.rank(candidates);
    }

    private void assignNextCandidate(DispatchSessionEntity session) {
        List<Long> candidates = parseCandidates(session.getRemainingCandidates());

        if (candidates.isEmpty()) {
            widenOrFail(session);
            return;
        }

        Long candidateId = candidates.get(0);
        List<Long> nextCandidates = candidates.size() > 1 ? candidates.subList(1, candidates.size()) : List.of();

        boolean reserved = reservationService.acquireReservation(
            candidateId, session.getDispatchId().toString(), session.getRideId().toString());

        if (!reserved) {
            try {
                session.setRemainingCandidates(objectMapper.writeValueAsString(nextCandidates));
            } catch (Exception e) {}
            dispatchRepository.save(session);
            assignNextCandidate(session);
            return;
        }

        session.setStatus(DispatchStatus.ASSIGNMENT_SENT);
        session.setCurrentDriverUserId(candidateId);
        
        try {
            session.setRemainingCandidates(objectMapper.writeValueAsString(nextCandidates));
        } catch (Exception e) {}
        
        session.setUpdatedAt(Instant.now());
        dispatchRepository.save(session);

        recordAttempt(session.getDispatchId(), candidateId, null, AttemptStatus.RESERVED, null);

        log.info("Assignment requested to driver {} for ride {}", candidateId, session.getRideId());
        publishAssignmentRequested(session, candidateId);

        cacheService.saveDispatchState(session.getDispatchId().toString(),
            DispatchStatus.ASSIGNMENT_SENT.name(), candidateId, session.getExpiresAt().toEpochMilli());
    }

    private void publishAssignmentRequested(DispatchSessionEntity session, Long driverUserId) {
        AssignmentRequestedEvent assignmentEvent = AssignmentRequestedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .dispatchId(session.getDispatchId())
            .rideId(session.getRideId())
            .driverUserId(driverUserId)
            .pickupLatitude(session.getPickupLatitude())
            .pickupLongitude(session.getPickupLongitude())
            .pickupLocation(session.getPickupLocation())
            .expiresAt(session.getExpiresAt())
            .build();

        eventProducer.publishAssignmentRequested(assignmentEvent);
        log.info("Published assignment requested for ride {} dispatch {} driver {}",
            session.getRideId(), session.getDispatchId(), driverUserId);
    }

    private void recordAttempt(UUID dispatchId, Long driverUserId, Double score, 
                               AttemptStatus status, String failureReason) {
        AssignmentAttempt attempt = new AssignmentAttempt();
        attempt.setRideId(dispatchId);
        attempt.setDriverUserId(driverUserId);
        attempt.setScore(score);
        attempt.setStatus(mapAttemptStatus(status));
        attempt.setFailureReason(failureReason);
        
        attemptRepository.save(attempt);
    }

    private AssignmentStatus mapAttemptStatus(AttemptStatus status) {
        return switch (status) {
            case RESERVED, ASSIGNMENT_SENT -> AssignmentStatus.CONSIDERED;
            case ACCEPTED -> AssignmentStatus.ASSIGNED;
            case REJECTED, TIMEOUT, FAILED -> AssignmentStatus.FAILED;
        };
    }

    private List<Long> parseCandidates(String candidatesJson) {
        if (candidatesJson == null || candidatesJson.isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(candidatesJson, 
                objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
        } catch (Exception e) {
            log.error("Failed to parse candidates: {}", candidatesJson, e);
            return List.of();
        }
    }
}
