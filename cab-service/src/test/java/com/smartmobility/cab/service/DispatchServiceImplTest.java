package com.smartmobility.cab.service.impl;

import com.smartmobility.cab.client.MatchmakingServiceClient;
import com.smartmobility.cab.dto.DispatchStatusResponse;
import com.smartmobility.cab.entity.RideEntity;
import com.smartmobility.cab.event.DriverResponseEvent;
import com.smartmobility.cab.kafka.RideEventProducer;
import com.smartmobility.cab.repository.RideRepository;
import com.smartmobility.cab.security.RideAuthorizationGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchServiceImplTest {

    @Mock
    private RideEventProducer eventProducer;

    @Mock
    private MatchmakingServiceClient matchmakingClient;

    @Mock
    private RideRepository rideRepository;

    private DispatchServiceImpl dispatchService;

    @BeforeEach
    void setUp() {
        dispatchService = new DispatchServiceImpl(
                eventProducer,
                matchmakingClient,
                rideRepository,
                new RideAuthorizationGuard()
        );
    }

    @Test
    void handleDriverResponse_Accepted_PublishesToAcceptedTopic() {
        UUID dispatchId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();
        Long driverUserId = 1L;
        RideEntity ride = RideEntity.builder()
                .riderUserId(100L)
                .id(rideId)
                .build();
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));

        dispatchService.handleDriverResponse(dispatchId, driverUserId, true, rideId, driverUserId);

        ArgumentCaptor<DriverResponseEvent> eventCaptor = ArgumentCaptor.forClass(DriverResponseEvent.class);
        verify(eventProducer).publishDriverResponse(eventCaptor.capture());

        DriverResponseEvent capturedEvent = eventCaptor.getValue();
        assertEquals(dispatchId, capturedEvent.getDispatchId());
        assertEquals(rideId, capturedEvent.getRideId());
        assertEquals(driverUserId, capturedEvent.getDriverUserId());
        assertTrue(capturedEvent.isAccepted());
    }

    @Test
    void handleDriverResponse_Rejected_PublishesToRejectedTopic() {
        UUID dispatchId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();
        Long driverUserId = 1L;
        RideEntity ride = RideEntity.builder()
                .riderUserId(100L)
                .id(rideId)
                .build();
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));

        dispatchService.handleDriverResponse(dispatchId, driverUserId, false, rideId, driverUserId);

        ArgumentCaptor<DriverResponseEvent> eventCaptor = ArgumentCaptor.forClass(DriverResponseEvent.class);
        verify(eventProducer).publishDriverResponse(eventCaptor.capture());

        DriverResponseEvent capturedEvent = eventCaptor.getValue();
        assertEquals(dispatchId, capturedEvent.getDispatchId());
        assertEquals(rideId, capturedEvent.getRideId());
        assertEquals(driverUserId, capturedEvent.getDriverUserId());
        assertFalse(capturedEvent.isAccepted());
    }

    @Test
    void handleDriverResponse_WhenCallerIsNotAssignedDriver_ThrowsForbidden() {
        UUID dispatchId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        assertThrows(
                com.smartmobility.cab.exception.ForbiddenAccessException.class,
                () -> dispatchService.handleDriverResponse(dispatchId, 1L, true, rideId, 2L)
        );

        verify(eventProducer, never()).publishDriverResponse(any());
    }

    @Test
    void handleDriverResponse_Accepted_WhenRideHasNoAssignedDriver_StillPublishesResponse() {
        UUID dispatchId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();
        Long driverUserId = 1L;
        RideEntity ride = RideEntity.builder()
                .riderUserId(100L)
                .id(rideId)
                .build();
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));

        dispatchService.handleDriverResponse(dispatchId, driverUserId, true, rideId, driverUserId);

        ArgumentCaptor<DriverResponseEvent> eventCaptor = ArgumentCaptor.forClass(DriverResponseEvent.class);
        verify(eventProducer).publishDriverResponse(eventCaptor.capture());

        DriverResponseEvent capturedEvent = eventCaptor.getValue();
        assertEquals(dispatchId, capturedEvent.getDispatchId());
        assertEquals(rideId, capturedEvent.getRideId());
        assertEquals(driverUserId, capturedEvent.getDriverUserId());
        assertTrue(capturedEvent.isAccepted());
    }

    @Test
    void cancelDispatch_LogsCancellation() {
        UUID rideId = UUID.randomUUID();
        RideEntity ride = RideEntity.builder()
                .driverUserId(1L)
                .riderUserId(1L)
                .id(rideId)
                .build();
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));

        dispatchService.cancelDispatch(rideId, "User requested", 1L);

        verify(eventProducer, never()).publishDriverResponse(any());
    }

    @Test
    void getDispatchStatus_Found_ReturnsStatus() {
        UUID rideId = UUID.randomUUID();
        DispatchStatusResponse expectedResponse = DispatchStatusResponse.builder()
                .dispatchId(UUID.randomUUID())
                .rideId(rideId)
                .status("ASSIGNED")
                .driverUserId(1L)
                .build();

        RideEntity ride = RideEntity.builder()
                .driverUserId(1L)
                .riderUserId(2L)
                .id(rideId)
                .build();
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(matchmakingClient.getDispatchStatus(rideId)).thenReturn(expectedResponse);

        Optional<DispatchStatusResponse> result = dispatchService.getDispatchStatus(rideId, 1L);

        assertTrue(result.isPresent());
        assertEquals("ASSIGNED", result.get().getStatus());
        assertEquals(1L, result.get().getDriverUserId());
    }

    @Test
    void getDispatchStatus_NotFound_ReturnsEmpty() {
        UUID rideId = UUID.randomUUID();
        RideEntity ride = RideEntity.builder()
                .driverUserId(1L)
                .riderUserId(2L)
                .id(rideId)
                .build();
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));

        when(matchmakingClient.getDispatchStatus(rideId)).thenReturn(null);

        Optional<DispatchStatusResponse> result = dispatchService.getDispatchStatus(rideId, 1L);

        assertTrue(result.isEmpty());
    }
}
