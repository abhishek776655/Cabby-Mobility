package com.smartmobility.matchmaking.kafka.consumer;

import com.smartmobility.matchmaking.event.AssignmentRejectedEvent;
import com.smartmobility.matchmaking.service.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmobility.matchmaking.exception.ReservationExpiredException;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentRejectedConsumer {

    private final DispatchService dispatchService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @KafkaListener(
        topics = "assignment-rejected",
        groupId = "matchmaking-service-group"
    )
    public void consume(String message) {
        log.info("Received AssignmentRejected: {}", message);
        AssignmentRejectedEvent event = null;
        try {
            event = objectMapper.readValue(message, AssignmentRejectedEvent.class);

            String redisKey = "processed:" + event.getEventId();
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", 24, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                log.info("Event {} already processed, skipping duplicate delivery.", event.getEventId());
                return;
            }

            if (event.getDispatchId() != null && event.getDriverUserId() != null) {
                dispatchService.handleDriverResponse(event.getDispatchId(), event.getDriverUserId(), false);
            }
        } catch (ReservationExpiredException e) {
            log.warn("Driver rejection arrived after reservation expired.");
            if (event != null && event.getDispatchId() != null) {
                dispatchService.handleDispatchTimeout(event.getDispatchId());
            }
        } catch (Exception e) {
            log.error("Failed to process AssignmentRejected: {}", message, e);
            throw new RuntimeException("Failed to process AssignmentRejected", e);
        }
    }
}
