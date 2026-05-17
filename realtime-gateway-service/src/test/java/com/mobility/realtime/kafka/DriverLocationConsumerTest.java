package com.mobility.realtime.kafka;

import com.mobility.realtime.dto.DriverLocationUpdatedEvent;
import com.mobility.realtime.exception.InvalidRealtimeEventException;
import com.mobility.realtime.service.RealtimeBroadcastService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DriverLocationConsumerTest {

    private final RealtimeBroadcastService broadcastService = mock(RealtimeBroadcastService.class);
    private final DriverLocationConsumer consumer = new DriverLocationConsumer(broadcastService);

    @Test
    void consumeBroadcastsValidLocationEvent() {
        DriverLocationUpdatedEvent event = DriverLocationUpdatedEvent.builder()
                .driverUserId("driver_1")
                .rideId("ride_123")
                .latitude(28.6139)
                .longitude(77.2090)
                .timestamp(Instant.parse("2026-05-17T12:00:00Z"))
                .build();

        consumer.consume(event);

        verify(broadcastService).broadcastDriverLocation(event);
    }

    @Test
    void consumeRejectsMissingRideId() {
        DriverLocationUpdatedEvent event = DriverLocationUpdatedEvent.builder()
                .driverUserId("driver_1")
                .latitude(28.6139)
                .longitude(77.2090)
                .timestamp(Instant.parse("2026-05-17T12:00:00Z"))
                .build();

        assertThrows(InvalidRealtimeEventException.class, () -> consumer.consume(event));
    }
}
