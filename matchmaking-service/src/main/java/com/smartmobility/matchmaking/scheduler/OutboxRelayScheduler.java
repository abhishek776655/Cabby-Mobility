package com.smartmobility.matchmaking.scheduler;

import com.smartmobility.matchmaking.entity.OutboxEvent;
import com.smartmobility.matchmaking.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayScheduler {

    // Consumers (e.g. cab-service) map this exact FQCN via spring.json.type.mapping to their own
    // local copy of the event class, and Spring's JsonSerializer derives the __TypeId__ header
    // from the ACTUAL RUNTIME CLASS of whatever object is sent — it always overwrites any header
    // set manually, so the payload must be deserialized back into its real event class (not a
    // generic JsonNode, and not the raw JSON String) before being handed to KafkaTemplate. Two
    // earlier attempts got this wrong:
    //   1. objectMapper.readTree(payload) sent a JsonNode — __TypeId__ became "...ObjectNode",
    //      which no consumer's type mapping recognized. Deserialization failed on every consumer,
    //      dead-lettered silently with no application-level exception on either side.
    //   2. Sending the raw JSON String value through a JsonSerializer double-encodes it — Jackson
    //      writes a quoted JSON string literal instead of the object, so consumers got
    //      "MismatchedInputException: no String-argument constructor" trying to build the target
    //      type from a plain string.
    private static final Map<String, Class<?>> EVENT_TYPE_TO_CLASS = Map.of(
            "assignment-requested", com.smartmobility.matchmaking.event.AssignmentRequestedEvent.class,
            "driver-assigned", com.smartmobility.matchmaking.event.DriverAssignedEvent.class,
            "matchmaking-failed", com.smartmobility.matchmaking.event.MatchmakingFailedEvent.class,
            "driver-assignment-failed", com.smartmobility.matchmaking.event.DriverAssignmentFailedEvent.class
    );

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relayEventsToKafka() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByProcessedFalseOrderByCreatedAtAsc();
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events to relay", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                Class<?> eventClass = EVENT_TYPE_TO_CLASS.get(event.getEventType());
                if (eventClass == null) {
                    throw new IllegalStateException("No event class mapping for event type: " + event.getEventType());
                }

                Object typedPayload = objectMapper.readValue(event.getPayload(), eventClass);

                // send() is async (returns a Future) — without waiting for the broker ack, a
                // transient/retriable send failure (leader not available, etc.) would still get
                // marked processed=true here and the event would be lost forever, silently,
                // since nothing would ever retry it.
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), typedPayload).get(10, TimeUnit.SECONDS);
                event.setProcessed(true);
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to relay outbox event ID: {}", event.getId(), e);
            }
        }
    }
}
