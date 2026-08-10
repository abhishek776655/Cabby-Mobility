package com.smartmobility.matchmaking.kafka;

import com.smartmobility.matchmaking.entity.OutboxEvent;
import com.smartmobility.matchmaking.event.AssignmentRequestedEvent;
import com.smartmobility.matchmaking.event.DriverAssignedEvent;
import com.smartmobility.matchmaking.event.DriverAssignmentFailedEvent;
import com.smartmobility.matchmaking.event.MatchmakingFailedEvent;
import com.smartmobility.matchmaking.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchmakingEventProducerImpl implements MatchmakingEventProducer {

    private static final String DRIVER_ASSIGNED_TOPIC = "driver-assigned";
    private static final String MATCHMAKING_FAILED_TOPIC = "matchmaking-failed";
    private static final String ASSIGNMENT_REQUESTED_TOPIC = "assignment-requested";
    private static final String DRIVER_ASSIGNMENT_FAILED_TOPIC = "driver-assignment-failed";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void publishAssignmentRequested(AssignmentRequestedEvent event) {
        save(ASSIGNMENT_REQUESTED_TOPIC, event.getRideId().toString(), "assignment-requested", event);
    }

    @Override
    @Transactional
    public void publishDriverAssigned(DriverAssignedEvent event) {
        save(DRIVER_ASSIGNED_TOPIC, event.getRideId().toString(), "driver-assigned", event);
    }

    @Override
    @Transactional
    public void publishMatchmakingFailed(MatchmakingFailedEvent event) {
        save(MATCHMAKING_FAILED_TOPIC, event.getRideId().toString(), "matchmaking-failed", event);
    }

    @Override
    @Transactional
    public void publishDriverAssignmentFailed(DriverAssignmentFailedEvent event) {
        save(DRIVER_ASSIGNMENT_FAILED_TOPIC, event.getDriverUserId().toString(), "driver-assignment-failed", event);
    }

    private void save(String topic, String aggregateId, String eventType, Object payload) {
        String json = objectMapper.writeValueAsString(payload);
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateId(aggregateId)
            .eventType(eventType)
            .topic(topic)
            .payload(json)
            .build();
        outboxEventRepository.save(outboxEvent);
        log.info("Queued {} event for ride/driver {} in outbox", eventType, aggregateId);
    }
}
