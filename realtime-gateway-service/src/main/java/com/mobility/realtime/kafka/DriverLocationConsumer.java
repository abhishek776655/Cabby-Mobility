package com.mobility.realtime.kafka;

import com.mobility.realtime.dto.DriverLocationUpdatedEvent;
import com.mobility.realtime.exception.InvalidRealtimeEventException;
import com.mobility.realtime.service.RealtimeBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverLocationConsumer {

    private final RealtimeBroadcastService broadcastService;

    @KafkaListener(
            topics = "${realtime.kafka.topics.driver-location-events}",
            containerFactory = "driverLocationKafkaListenerContainerFactory"
    )
    public void consume(DriverLocationUpdatedEvent event) {
        if (event == null || event.getRideId() == null || event.getRideId().isBlank()) {
            throw new InvalidRealtimeEventException("Driver location event must include rideId");
        }
        log.info("Consumed driver location event rideId={} driverUserId={} lat={} lng={}",
                event.getRideId(), event.getDriverUserId(), event.getLatitude(), event.getLongitude());
        broadcastService.broadcastDriverLocation(event);
    }
}
