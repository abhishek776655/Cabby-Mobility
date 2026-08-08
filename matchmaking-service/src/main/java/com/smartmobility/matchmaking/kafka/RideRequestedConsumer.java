package com.smartmobility.matchmaking.kafka;

import com.smartmobility.matchmaking.event.RideRequestedEvent;
import com.smartmobility.matchmaking.service.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideRequestedConsumer {

    private final DispatchService dispatchService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "ride-requested",
            groupId = "matchmaking-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(String message) {
        log.info("Received ride requested message: {}", message);
        try {
            RideRequestedEvent event = objectMapper.readValue(message, RideRequestedEvent.class);
            dispatchService.startDispatch(event);
        } catch (Exception e) {
            log.error("Error processing ride requested message: {}", message, e);
            throw new RuntimeException("Failed to process message", e);
        }
    }
}
