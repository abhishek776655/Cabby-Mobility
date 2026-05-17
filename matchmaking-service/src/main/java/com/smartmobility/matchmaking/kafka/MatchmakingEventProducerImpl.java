package com.smartmobility.matchmaking.kafka;

import com.smartmobility.matchmaking.event.AssignmentRequestedEvent;
import com.smartmobility.matchmaking.event.DriverAssignedEvent;
import com.smartmobility.matchmaking.event.MatchmakingFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchmakingEventProducerImpl implements MatchmakingEventProducer {

    private static final String DRIVER_ASSIGNED_TOPIC = "driver-assigned";
    private static final String MATCHMAKING_FAILED_TOPIC = "matchmaking-failed";
    private static final String ASSIGNMENT_REQUESTED_TOPIC = "assignment-requested";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishAssignmentRequested(AssignmentRequestedEvent event) {
        log.info("Publishing assignment requested event for ride: {} driver: {}",
                event.getRideId(), event.getDriverUserId());
        kafkaTemplate.send(ASSIGNMENT_REQUESTED_TOPIC, event.getRideId().toString(), event);
    }

    @Override
    public void publishDriverAssigned(DriverAssignedEvent event) {
        log.info("Publishing driver assigned event for ride: {}", event.getRideId());
        kafkaTemplate.send(DRIVER_ASSIGNED_TOPIC, event.getRideId().toString(), event);
    }

    @Override
    public void publishMatchmakingFailed(MatchmakingFailedEvent event) {
        log.info("Publishing matchmaking failed event for ride: {}", event.getRideId());
        kafkaTemplate.send(MATCHMAKING_FAILED_TOPIC, event.getRideId().toString(), event);
    }
}
