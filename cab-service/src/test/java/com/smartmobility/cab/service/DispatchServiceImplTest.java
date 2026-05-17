package com.smartmobility.cab.service.impl;

import com.smartmobility.cab.client.MatchmakingServiceClient;
import com.smartmobility.cab.dto.DispatchStatusResponse;
import com.smartmobility.cab.event.DriverResponseEvent;
import com.smartmobility.cab.kafka.RideEventProducer;
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

    private DispatchServiceImpl dispatchService;

    @BeforeEach
    void setUp() {
        dispatchService = new DispatchServiceImpl(eventProducer, matchmakingClient);
    }

    @Test
    void handleDriverResponse_Accepted_PublishesToAcceptedTopic() {
        UUID dispatchId = UUID.randomUUID();
        Long driverUserId = 1L;

        dispatchService.handleDriverResponse(dispatchId, driverUserId, true);

        ArgumentCaptor<DriverResponseEvent> eventCaptor = ArgumentCaptor.forClass(DriverResponseEvent.class);
        verify(eventProducer).publishDriverResponse(eventCaptor.capture());

        DriverResponseEvent capturedEvent = eventCaptor.getValue();
        assertEquals(dispatchId, capturedEvent.getDispatchId());
        assertEquals(driverUserId, capturedEvent.getDriverUserId());
        assertTrue(capturedEvent.isAccepted());
    }

    @Test
    void handleDriverResponse_Rejected_PublishesToRejectedTopic() {
        UUID dispatchId = UUID.randomUUID();
        Long driverUserId = 1L;

        dispatchService.handleDriverResponse(dispatchId, driverUserId, false);

        ArgumentCaptor<DriverResponseEvent> eventCaptor = ArgumentCaptor.forClass(DriverResponseEvent.class);
        verify(eventProducer).publishDriverResponse(eventCaptor.capture());

        DriverResponseEvent capturedEvent = eventCaptor.getValue();
        assertEquals(dispatchId, capturedEvent.getDispatchId());
        assertEquals(driverUserId, capturedEvent.getDriverUserId());
        assertFalse(capturedEvent.isAccepted());
    }

    @Test
    void cancelDispatch_LogsCancellation() {
        UUID rideId = UUID.randomUUID();

        dispatchService.cancelDispatch(rideId, "User requested");

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

        when(matchmakingClient.getDispatchStatus(rideId)).thenReturn(expectedResponse);

        Optional<DispatchStatusResponse> result = dispatchService.getDispatchStatus(rideId);

        assertTrue(result.isPresent());
        assertEquals("ASSIGNED", result.get().getStatus());
        assertEquals(1L, result.get().getDriverUserId());
    }

    @Test
    void getDispatchStatus_NotFound_ReturnsEmpty() {
        UUID rideId = UUID.randomUUID();

        when(matchmakingClient.getDispatchStatus(rideId)).thenReturn(null);

        Optional<DispatchStatusResponse> result = dispatchService.getDispatchStatus(rideId);

        assertTrue(result.isEmpty());
    }
}
