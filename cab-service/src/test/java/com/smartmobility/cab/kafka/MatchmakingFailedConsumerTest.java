package com.smartmobility.cab.kafka;

import com.smartmobility.cab.event.MatchmakingFailedEvent;
import com.smartmobility.cab.service.RideService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchmakingFailedConsumerTest {

    @Mock
    private RideService rideService;

    @Test
    void consume_updatesRideOnFailureEvent() {
        MatchmakingFailedConsumer consumer = new MatchmakingFailedConsumer(rideService);
        UUID rideId = UUID.randomUUID();
        MatchmakingFailedEvent event = MatchmakingFailedEvent.builder()
                .eventId("evt-123")
                .rideId(rideId)
                .reason("NO_DRIVER_AVAILABLE")
                .failedAt(LocalDateTime.now())
                .build();

        consumer.consume(event);

        verify(rideService).handleMatchmakingFailedEvent("evt-123", rideId, "NO_DRIVER_AVAILABLE");
    }
}
