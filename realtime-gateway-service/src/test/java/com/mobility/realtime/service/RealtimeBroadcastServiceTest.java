package com.mobility.realtime.service;

import com.mobility.realtime.dto.AssignmentRequestedEvent;
import com.mobility.realtime.dto.DriverLocationUpdatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RealtimeBroadcastServiceTest {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final RealtimeBroadcastService broadcastService = new RealtimeBroadcastService(messagingTemplate);

    @Test
    void broadcastDriverLocationSendsToTripTopic() {
        DriverLocationUpdatedEvent event = DriverLocationUpdatedEvent.builder()
                .driverUserId("driver_1")
                .rideId("ride_123")
                .latitude(28.6139)
                .longitude(77.2090)
                .speed(42.0)
                .heading(120.0)
                .timestamp(Instant.parse("2026-05-17T12:00:00Z"))
                .build();

        broadcastService.broadcastDriverLocation(event);

        verify(messagingTemplate).convertAndSend("/topic/trip/ride_123", event);
    }

    @Test
    void broadcastDriverLocationRejectsMissingRideId() {
        DriverLocationUpdatedEvent event = DriverLocationUpdatedEvent.builder()
                .driverUserId("driver_1")
                .latitude(28.6139)
                .longitude(77.2090)
                .timestamp(Instant.parse("2026-05-17T12:00:00Z"))
                .build();

        assertThrows(IllegalArgumentException.class, () -> broadcastService.broadcastDriverLocation(event));
    }

    @Test
    void broadcastAssignmentRequestSendsToDriverTopic() {
        AssignmentRequestedEvent event = AssignmentRequestedEvent.builder()
                .driverUserId(1L)
                .pickupLatitude(28.6139)
                .pickupLongitude(77.2090)
                .pickupLocation("Connaught Place")
                .expiresAt(Instant.parse("2026-05-17T12:00:15Z"))
                .build();

        broadcastService.broadcastAssignmentRequest(event);

        verify(messagingTemplate).convertAndSend("/topic/driver/1", event);
    }

    @Test
    void broadcastAssignmentRequestRejectsMissingDriverUserId() {
        AssignmentRequestedEvent event = AssignmentRequestedEvent.builder().build();

        assertThrows(IllegalArgumentException.class, () -> broadcastService.broadcastAssignmentRequest(event));
    }
}
