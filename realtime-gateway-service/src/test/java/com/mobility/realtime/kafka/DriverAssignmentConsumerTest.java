package com.mobility.realtime.kafka;

import com.mobility.realtime.dto.AssignmentRequestedEvent;
import com.mobility.realtime.exception.InvalidRealtimeEventException;
import com.mobility.realtime.service.RealtimeBroadcastService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DriverAssignmentConsumerTest {

    private final RealtimeBroadcastService broadcastService = mock(RealtimeBroadcastService.class);
    private final DriverAssignmentConsumer consumer = new DriverAssignmentConsumer(broadcastService);

    @Test
    void consumeBroadcastsValidAssignmentEvent() {
        AssignmentRequestedEvent event = AssignmentRequestedEvent.builder()
                .eventId("evt_1")
                .dispatchId(UUID.randomUUID())
                .rideId(UUID.randomUUID())
                .driverUserId(1L)
                .pickupLatitude(28.6139)
                .pickupLongitude(77.2090)
                .pickupLocation("Connaught Place")
                .expiresAt(Instant.parse("2026-05-17T12:00:15Z"))
                .build();

        consumer.consume(event);

        verify(broadcastService).broadcastAssignmentRequest(event);
    }

    @Test
    void consumeRejectsMissingDriverUserId() {
        AssignmentRequestedEvent event = AssignmentRequestedEvent.builder()
                .eventId("evt_1")
                .build();

        assertThrows(InvalidRealtimeEventException.class, () -> consumer.consume(event));
    }
}
