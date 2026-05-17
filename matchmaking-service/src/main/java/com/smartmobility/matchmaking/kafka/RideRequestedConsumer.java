package com.smartmobility.matchmaking.kafka;

import com.smartmobility.matchmaking.event.RideRequestedEvent;
import com.smartmobility.matchmaking.service.MatchmakingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideRequestedConsumer {

    private final MatchmakingService matchmakingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(
            topics = "ride-requested",
            groupId = "matchmaking-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(String message) {
        log.info("Received ride requested message: {}", message);
        try {
            RideRequestedEvent event = objectMapper.readValue(message, RideRequestedEvent.class);
            matchmakingService.matchRide(event);
        } catch (Exception e) {
            log.error("Error processing ride requested message: {}", message, e);
            throw new RuntimeException("Failed to process message", e);
        }
    }
}
