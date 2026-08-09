package com.smartmobility.notification_service.kafka.consumer;

import com.smartmobility.notification_service.event.MatchmakingFailedEvent;
import com.smartmobility.notification_service.service.NotificationDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchmakingFailedNotificationConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationDeliveryService deliveryService;

    @KafkaListener(topics = "matchmaking-failed", groupId = "notification-service-group", containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message) {
        log.info("Received matchmaking-failed event");
        try {
            MatchmakingFailedEvent event = objectMapper.readValue(message, MatchmakingFailedEvent.class);
            deliveryService.deliver(
                event.getEventId(), 
                event.getRiderUserId(), 
                "MATCHMAKING_FAILED", 
                "PUSH", 
                "No driver could be found for your ride: " + event.getReason()
            );
        } catch (Exception e) {
            log.error("Failed to parse MatchmakingFailedEvent: {}", message, e);
            throw new IllegalArgumentException("Invalid message format", e);
        }
    }
}
