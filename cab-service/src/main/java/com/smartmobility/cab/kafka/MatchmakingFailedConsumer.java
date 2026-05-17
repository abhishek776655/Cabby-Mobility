package com.smartmobility.cab.kafka;

import com.smartmobility.cab.event.MatchmakingFailedEvent;
import com.smartmobility.cab.service.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchmakingFailedConsumer {

    private final RideService rideService;

    @KafkaListener(
            topics = "matchmaking-failed",
            groupId = "cab-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(MatchmakingFailedEvent event) {
        log.info("Received MatchmakingFailed: rideId={}, reason={}", event.getRideId(), event.getReason());
        rideService.handleMatchmakingFailedEvent(
                event.getEventId(),
                event.getRideId(),
                event.getReason()
        );
    }
}