package com.smartmobility.notification_service.kafka.consumer;

import com.smartmobility.notification_service.event.AssignmentRequestedEvent;
import com.smartmobility.notification_service.service.NotificationDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentRequestedNotificationConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationDeliveryService deliveryService;

    @KafkaListener(topics = "assignment-requested", groupId = "notification-service-group", containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message) {
        log.info("Received assignment-requested event");
        try {
            AssignmentRequestedEvent event = objectMapper.readValue(message, AssignmentRequestedEvent.class);
            deliveryService.deliver(
                event.getEventId(), 
                event.getDriverUserId(), // NOTE: Driver User ID 
                "ASSIGNMENT_REQUESTED", 
                "PUSH", 
                "You have a new ride offer"
            );
        } catch (Exception e) {
            log.error("Failed to parse AssignmentRequestedEvent: {}", message, e);
            throw new IllegalArgumentException("Invalid message format", e);
        }
    }
}
