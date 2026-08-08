package com.smartmobility.cab.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmobility.cab.entity.OutboxEvent;
import com.smartmobility.cab.event.DriverResponseEvent;
import com.smartmobility.cab.event.RideCancelledEvent;
import com.smartmobility.cab.event.RideCompletedEvent;
import com.smartmobility.cab.event.RideRequestedEvent;
import com.smartmobility.cab.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideEventProducer {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    private static final String RIDE_REQUESTED_TOPIC = "ride-requested";
    private static final String ASSIGNMENT_ACCEPTED_TOPIC = "assignment-accepted";
    private static final String ASSIGNMENT_REJECTED_TOPIC = "assignment-rejected";
    private static final String RIDE_COMPLETED_TOPIC = "ride-completed";
    private static final String RIDE_CANCELLED_TOPIC = "ride-cancelled";

    public void publishRideRequested(RideRequestedEvent event) {
        String key = event.getRideId().toString();
        saveToOutbox(event, RIDE_REQUESTED_TOPIC, key, "RideRequestedEvent");
    }

    public void publishDriverResponse(DriverResponseEvent event) {
        String topic = event.isAccepted() ? ASSIGNMENT_ACCEPTED_TOPIC : ASSIGNMENT_REJECTED_TOPIC;
        String key = event.getRideId() != null
                ? event.getRideId().toString()
                : event.getDispatchId().toString();
        saveToOutbox(event, topic, key, "DriverResponseEvent");
    }

    public void publishRideCompleted(RideCompletedEvent event) {
        saveToOutbox(event, RIDE_COMPLETED_TOPIC, event.getRideId().toString(), "RideCompletedEvent");
    }

    public void publishRideCancelled(RideCancelledEvent event) {
        saveToOutbox(event, RIDE_CANCELLED_TOPIC, event.getRideId().toString(), "RideCancelledEvent");
    }

    private void saveToOutbox(Object event, String topic, String aggregateId, String eventType) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .topic(topic)
                    .payload(payload)
                    .processed(false)
                    .createdAt(LocalDateTime.now())
                    .build();
                    
            outboxEventRepository.save(outboxEvent);
            log.debug("Saved event to outbox: {} for aggregateId: {}", eventType, aggregateId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", event, e);
            throw new RuntimeException("Failed to serialize event for outbox", e);
        }
    }
}
