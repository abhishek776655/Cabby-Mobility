package com.smartmobility.notification_service.kafka.consumer;

import com.smartmobility.notification_service.event.RideCancelledEvent;
import com.smartmobility.notification_service.service.NotificationDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideCancelledNotificationConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationDeliveryService deliveryService;

    @KafkaListener(topics = "ride-cancelled", groupId = "notification-service-group", containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message) {
        log.info("Received ride-cancelled event");
        try {
            RideCancelledEvent event = objectMapper.readValue(message, RideCancelledEvent.class);
            deliveryService.deliver(
                event.getEventId(), 
                event.getRiderUserId(), 
                "RIDE_CANCELLED", 
                "PUSH", 
                "Your ride was cancelled"
            );
        } catch (Exception e) {
            log.error("Failed to parse RideCancelledEvent: {}", message, e);
            throw new IllegalArgumentException("Invalid message format", e);
        }
    }
}
