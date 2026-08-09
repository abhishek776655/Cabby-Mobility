package com.smartmobility.notification_service.kafka.consumer;

import com.smartmobility.notification_service.event.DriverAssignedEvent;
import com.smartmobility.notification_service.service.NotificationDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverAssignedNotificationConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationDeliveryService deliveryService;

    @KafkaListener(topics = "driver-assigned", groupId = "notification-service-group", containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message) {
        log.info("Received driver-assigned event");
        try {
            DriverAssignedEvent event = objectMapper.readValue(message, DriverAssignedEvent.class);
            deliveryService.deliver(
                event.getEventId(), 
                event.getRiderUserId(), 
                "DRIVER_ASSIGNED", 
                "PUSH", 
                "Your driver has been assigned"
            );
        } catch (Exception e) {
            log.error("Failed to parse DriverAssignedEvent: {}", message, e);
            throw new IllegalArgumentException("Invalid message format", e);
        }
    }
}
