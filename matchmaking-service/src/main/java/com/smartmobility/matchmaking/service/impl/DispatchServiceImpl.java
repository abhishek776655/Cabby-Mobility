package com.smartmobility.matchmaking.service.impl;

import com.smartmobility.matchmaking.client.DriverServiceClient;
import com.smartmobility.matchmaking.client.LocationServiceClient;
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
    private final ReservationService reservationService;
    private final DispatchCacheService cacheService;
    private final MatchmakingEventProducer eventProducer;
    private final ObjectMapper objectMapper;

    @Value("${matchmaking.default-radius-km:5}")
    private double discoveryRadiusKm;

    @Value("${matchmaking.default-limit:10}")
    private int discoveryLimit;

    private MatchmakingProperties properties;

    public void setProperties(MatchmakingProperties properties) {
        this.properties = properties;
    }

    @Override
    @Transactional
    public void startDispatch(RideRequestedEvent event) {
        if (dispatchRepository.findByRideId(event.getRideId()).isPresent()) {
            log.info("Dispatch already exists for ride {}", event.getRideId());
            return;
        }

        List<Long> nearbyDriverUserIds = locationClient.findNearbyDrivers(
            event.getPickupLatitude(), event.getPickupLongitude(),
            discoveryRadiusKm, discoveryLimit);

        if (nearbyDriverUserIds.isEmpty()) {
            publishNoDriverFound(event, "NO_DRIVER_AVAILABLE");
            return;
        }

        List<Long> eligibleDriverUserIds = filterEligibleDrivers(nearbyDriverUserIds);

        if (eligibleDriverUserIds.isEmpty()) {
            publishNoDriverFound(event, "NO_DRIVER_AVAILABLE");
            return;
        }

        List<Long> rankedDriverUserIds = rankDrivers(eligibleDriverUserIds, event.getPickupLatitude(), event.getPickupLongitude());

        Instant expiresAt = Instant.now().plus(30, ChronoUnit.SECONDS);
        
        DispatchSessionEntity session = new DispatchSessionEntity();
        session.setDispatchId(UUID.randomUUID());
        session.setRideId(event.getRideId());
        session.setRiderUserId(event.getRiderUserId());
        session.setStatus(DispatchStatus.SEARCHING);
        session.setCurrentDriverUserId(null);
        session.setRetryCount(0);
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(expiresAt);
        session.setUpdatedAt(Instant.now());
        
        try {
            session.setRemainingCandidates(objectMapper.writeValueAsString(rankedDriverUserIds));
        } catch (Exception e) {
            log.error("Failed to serialize candidates", e);
        }

        dispatchRepository.save(session);
        
        cacheService.saveDispatchState(session.getDispatchId().toString(), 
            DispatchStatus.SEARCHING.name(), null, expiresAt.toEpochMilli());

        assignNextCandidate(session, event);
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

    private void handleAcceptance(DispatchSessionEntity session, Long driverUserId) {
        reservationService.releaseReservation(driverUserId, session.getDispatchId().toString());

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
            completeWithFailure(session, "NO_DRIVER_AVAILABLE");
        } else {
            retryWithNextCandidate(session, remaining);
        }
    }

    private void retryWithNextCandidate(DispatchSessionEntity session, List<Long> remainingDrivers) {
        if (remainingDrivers.isEmpty()) {
            completeWithFailure(session, "NO_DRIVER_AVAILABLE");
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

        session.setStatus(DispatchStatus.RETRYING);
        session.setCurrentDriverUserId(nextDriver);
        session.setRetryCount(session.getRetryCount() + 1);
        
        try {
            session.setRemainingCandidates(objectMapper.writeValueAsString(nextList));
        } catch (Exception e) {}
        
        session.setUpdatedAt(Instant.now());
        dispatchRepository.save(session);

        recordAttempt(session.getDispatchId(), nextDriver, null, AttemptStatus.RESERVED, null);

        log.info("Retry: Assignment requested to driver {} for ride {}", nextDriver, session.getRideId());

        cacheService.saveDispatchState(session.getDispatchId().toString(),
            DispatchStatus.ASSIGNMENT_SENT.name(), nextDriver, session.getExpiresAt().toEpochMilli());
    }

    private void completeWithFailure(DispatchSessionEntity session, String reason) {
        session.setStatus(DispatchStatus.FAILED);
        session.setUpdatedAt(Instant.now());
        dispatchRepository.save(session);

        MatchmakingFailedEvent failedEvent = MatchmakingFailedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .rideId(session.getRideId())
            .reason(reason)
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
        return driverUserIds;
    }

    private void assignNextCandidate(DispatchSessionEntity session, RideRequestedEvent event) {
        List<Long> candidates = parseCandidates(session.getRemainingCandidates());
        
        if (candidates.isEmpty()) {
            completeWithFailure(session, "NO_DRIVER_AVAILABLE");
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
            assignNextCandidate(session, event);
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

        AssignmentRequestedEvent assignmentEvent = AssignmentRequestedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .dispatchId(session.getDispatchId())
            .rideId(session.getRideId())
            .driverUserId(candidateId)
            .pickupLatitude(event.getPickupLatitude())
            .pickupLongitude(event.getPickupLongitude())
            .pickupLocation(event.getPickupLocation())
            .expiresAt(session.getExpiresAt())
            .build();
        
        log.info("Assignment requested to driver {} for ride {}", candidateId, session.getRideId());
        eventProducer.publishAssignmentRequested(assignmentEvent);

        cacheService.saveDispatchState(session.getDispatchId().toString(),
            DispatchStatus.ASSIGNMENT_SENT.name(), candidateId, session.getExpiresAt().toEpochMilli());
    }

    private void recordAttempt(UUID dispatchId, Long driverUserId, Double score, 
                               AttemptStatus status, String failureReason) {
        AssignmentAttempt attempt = new AssignmentAttempt();
        attempt.setRideId(dispatchId);
        attempt.setDriverUserId(driverUserId);
        attempt.setScore(score);
        attempt.setStatus(AssignmentStatus.valueOf(status.name()));
        attempt.setFailureReason(failureReason);
        
        attemptRepository.save(attempt);
    }

    private void publishNoDriverFound(RideRequestedEvent event, String reason) {
        MatchmakingFailedEvent failedEvent = MatchmakingFailedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .rideId(event.getRideId())
            .reason(reason)
            .failedAt(java.time.LocalDateTime.now())
            .build();
        
        eventProducer.publishMatchmakingFailed(failedEvent);
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
