package com.smartmobility.matchmaking.service.impl;

import com.smartmobility.matchmaking.client.DriverServiceClient;
import com.smartmobility.matchmaking.client.LocationServiceClient;
import com.smartmobility.matchmaking.config.MatchmakingProperties;
import com.smartmobility.matchmaking.domain.DispatchStatus;
import com.smartmobility.matchmaking.dto.DriverResponseDTO;
import com.smartmobility.matchmaking.entity.DispatchSessionEntity;
import com.smartmobility.matchmaking.event.AssignmentRequestedEvent;
import com.smartmobility.matchmaking.event.RideRequestedEvent;
import com.smartmobility.matchmaking.kafka.MatchmakingEventProducer;
import com.smartmobility.matchmaking.redis.DispatchCacheService;
import com.smartmobility.matchmaking.redis.ReservationService;
import com.smartmobility.matchmaking.repository.AssignmentAttemptRepository;
import com.smartmobility.matchmaking.repository.DispatchSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchServiceImplTest {

    @Mock
    private DispatchSessionRepository dispatchRepository;

    @Mock
    private AssignmentAttemptRepository attemptRepository;

    @Mock
    private LocationServiceClient locationClient;

    @Mock
    private DriverServiceClient driverClient;

    @Mock
    private ReservationService reservationService;

    @Mock
    private DispatchCacheService cacheService;

    @Mock
    private MatchmakingEventProducer eventProducer;

    private MatchmakingProperties properties;

    private DispatchServiceImpl dispatchService;

    @BeforeEach
    void setUp() {
        properties = new MatchmakingProperties();
        properties.getAssignment().setTimeoutSeconds(30);

        dispatchService = new DispatchServiceImpl(
                dispatchRepository,
                attemptRepository,
                locationClient,
                driverClient,
                reservationService,
                cacheService,
                eventProducer,
                new ObjectMapper(),
                properties
        );
    }

    @Test
    void startDispatch_usesConfiguredTimeoutForExpiresAt() {
        UUID rideId = UUID.randomUUID();
        RideRequestedEvent event = RideRequestedEvent.builder()
                .eventId("event-1")
                .rideId(rideId)
                .riderUserId(1L)
                .pickupLocation("Connaught Place")
                .pickupLatitude(28.6139)
                .pickupLongitude(77.2090)
                .dropLocation("India Gate")
                .dropLatitude(28.6129)
                .dropLongitude(77.2295)
                .build();

        DriverResponseDTO driver = new DriverResponseDTO();
        driver.setAvailable(true);

        when(locationClient.findNearbyDrivers(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(90L));
        when(driverClient.getDriver(90L)).thenReturn(driver);
        when(reservationService.acquireReservation(eq(90L), anyString(), eq(rideId.toString())))
                .thenReturn(true);

        dispatchService.startDispatch(event);

        ArgumentCaptor<DispatchSessionEntity> sessionCaptor = ArgumentCaptor.forClass(DispatchSessionEntity.class);
        verify(dispatchRepository, atLeastOnce()).save(sessionCaptor.capture());
        DispatchSessionEntity savedSession = sessionCaptor.getValue();

        assertEquals(rideId, savedSession.getRideId());
        assertEquals(DispatchStatus.ASSIGNMENT_SENT, savedSession.getStatus());
        assertEquals("Connaught Place", savedSession.getPickupLocation());
        assertTrue(savedSession.getExpiresAt().isAfter(savedSession.getCreatedAt().plusSeconds(29)));
    }

    @Test
    void handleDispatchTimeout_WhenCandidatesRemain_PublishesNextAssignmentRequest() {
        UUID dispatchId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();
        DispatchSessionEntity session = new DispatchSessionEntity();
        session.setDispatchId(dispatchId);
        session.setRideId(rideId);
        session.setRiderUserId(1L);
        session.setPickupLatitude(28.6139);
        session.setPickupLongitude(77.2090);
        session.setPickupLocation("Connaught Place");
        session.setStatus(DispatchStatus.ASSIGNMENT_SENT);
        session.setCurrentDriverUserId(90L);
        session.setRemainingCandidates("[95]");
        session.setRetryCount(0);
        session.setCreatedAt(Instant.now().minusSeconds(40));
        session.setExpiresAt(Instant.now().minusSeconds(5));
        session.setUpdatedAt(Instant.now().minusSeconds(5));

        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(session));
        when(reservationService.releaseReservation(90L, dispatchId.toString())).thenReturn(true);
        when(reservationService.acquireReservation(95L, dispatchId.toString(), rideId.toString())).thenReturn(true);

        dispatchService.handleDispatchTimeout(dispatchId);

        ArgumentCaptor<AssignmentRequestedEvent> eventCaptor = ArgumentCaptor.forClass(AssignmentRequestedEvent.class);
        verify(eventProducer).publishAssignmentRequested(eventCaptor.capture());
        assertEquals(dispatchId, eventCaptor.getValue().getDispatchId());
        assertEquals(rideId, eventCaptor.getValue().getRideId());
        assertEquals(95L, eventCaptor.getValue().getDriverUserId());
        assertEquals("Connaught Place", eventCaptor.getValue().getPickupLocation());
        assertTrue(session.getExpiresAt().isAfter(Instant.now()));
        verify(reservationService).releaseReservation(90L, dispatchId.toString());
        verify(reservationService).acquireReservation(95L, dispatchId.toString(), rideId.toString());
    }

    @Test
    void handleDriverResponse_WhenAccepted_ExtendsReservationInsteadOfReleasingIt() {
        UUID dispatchId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();
        DispatchSessionEntity session = new DispatchSessionEntity();
        session.setDispatchId(dispatchId);
        session.setRideId(rideId);
        session.setCurrentDriverUserId(90L);
        session.setStatus(DispatchStatus.ASSIGNMENT_SENT);

        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(session));
        when(reservationService.hasActiveReservation(90L)).thenReturn(true);

        dispatchService.handleDriverResponse(dispatchId, 90L, true);

        verify(reservationService).extendReservation(
                90L, dispatchId.toString(), properties.getOnTripReservationSeconds());
        verify(reservationService, never()).releaseReservation(90L, dispatchId.toString());
        assertEquals(DispatchStatus.ASSIGNED, session.getStatus());
    }
}
