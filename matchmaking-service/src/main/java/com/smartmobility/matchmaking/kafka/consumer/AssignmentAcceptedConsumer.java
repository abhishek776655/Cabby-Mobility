package com.smartmobility.matchmaking.kafka.consumer;

import com.smartmobility.matchmaking.event.AssignmentAcceptedEvent;
import com.smartmobility.matchmaking.service.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.smartmobility.matchmaking.exception.ReservationExpiredException;
import com.smartmobility.matchmaking.event.DriverAssignmentFailedEvent;
import com.smartmobility.matchmaking.kafka.MatchmakingEventProducer;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentAcceptedConsumer {

    private final DispatchService dispatchService;
    private final ObjectMapper objectMapper;
    private final MatchmakingEventProducer eventProducer;
    private final StringRedisTemplate redisTemplate;

    @KafkaListener(
        topics = "assignment-accepted",
        groupId = "matchmaking-service-group"
    )
    public void consume(String message) {
        log.info("Received AssignmentAccepted: {}", message);
        AssignmentAcceptedEvent event = null;
        try {
            event = objectMapper.readValue(message, AssignmentAcceptedEvent.class);
            
            String redisKey = "processed:" + event.getEventId();
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", 24, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                log.info("Event {} already processed, skipping duplicate delivery.", event.getEventId());
                return;
            }
            
            if (event.getDispatchId() != null && event.getDriverUserId() != null) {
                dispatchService.handleDriverResponse(event.getDispatchId(), event.getDriverUserId(), true);
            }
        } catch (ReservationExpiredException e) {
            log.warn("Driver was too slow. Reservation expired.");
            if (event != null && event.getDriverUserId() != null) {
                publishFailure(event, "RIDE_TIMED_OUT");
            }
        } catch (Exception e) {
            log.error("Failed to process AssignmentAccepted: {}", message, e);
            if (event != null && event.getDriverUserId() != null) {
                publishFailure(event, "SYSTEM_ERROR");
            }
            throw new RuntimeException("Failed to process AssignmentAccepted", e);
        }
    }

    private void publishFailure(AssignmentAcceptedEvent event, String reason) {
        DriverAssignmentFailedEvent failureEvent = DriverAssignmentFailedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .dispatchId(event.getDispatchId())
                .rideId(event.getRideId())
                .driverUserId(event.getDriverUserId())
                .reason(reason)
                .failedAt(Instant.now())
                .build();
        eventProducer.publishDriverAssignmentFailed(failureEvent);
    }
}
