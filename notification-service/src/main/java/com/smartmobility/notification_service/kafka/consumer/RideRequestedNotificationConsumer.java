package com.smartmobility.notification_service.kafka.consumer;

import com.smartmobility.notification_service.event.RideRequestedEvent;
import com.smartmobility.notification_service.service.NotificationDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideRequestedNotificationConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationDeliveryService deliveryService;

    @KafkaListener(topics = "ride-requested", groupId = "notification-service-group", containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message) {
        log.info("Received ride-requested event");
        try {
            RideRequestedEvent event = objectMapper.readValue(message, RideRequestedEvent.class);
            deliveryService.deliver(
                event.getEventId(), 
                event.getRiderUserId(), 
                "RIDE_REQUESTED", 
                "PUSH", 
                "We're finding you a driver"
            );
        } catch (Exception e) {
            log.error("Failed to parse RideRequestedEvent: {}", message, e);
            throw new IllegalArgumentException("Invalid message format", e);
        }
    }
}
