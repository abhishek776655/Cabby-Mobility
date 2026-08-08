package com.smartmobility.matchmaking.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmobility.matchmaking.entity.DispatchSessionEntity;
import com.smartmobility.matchmaking.event.RideCancelledEvent;
import com.smartmobility.matchmaking.redis.ReservationService;
import com.smartmobility.matchmaking.repository.DispatchSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Releases the driver's on-trip reservation if a ride they were already assigned to gets
 * cancelled mid-trip, instead of leaving them falsely reserved until the safety-net TTL expires.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideCancelledConsumer {

    private final DispatchSessionRepository dispatchRepository;
    private final ReservationService reservationService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @KafkaListener(
        topics = "ride-cancelled",
        groupId = "matchmaking-service-group"
    )
    public void consume(String message) {
        log.info("Received RideCancelled: {}", message);
        try {
            RideCancelledEvent event = objectMapper.readValue(message, RideCancelledEvent.class);

            String redisKey = "processed:" + event.getEventId();
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", 24, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                log.info("Event {} already processed, skipping duplicate delivery.", event.getEventId());
                return;
            }

            releaseDriverForRide(event.getRideId(), event.getDriverUserId());
        } catch (Exception e) {
            log.error("Failed to process RideCancelled: {}", message, e);
            throw new RuntimeException("Failed to process RideCancelled", e);
        }
    }

    private void releaseDriverForRide(UUID rideId, Long driverUserId) {
        if (driverUserId == null) {
            return;
        }

        Optional<DispatchSessionEntity> session = dispatchRepository.findByRideId(rideId);
        if (session.isEmpty()) {
            log.warn("No dispatch session found for cancelled ride {}, cannot release driver {}",
                    rideId, driverUserId);
            return;
        }

        reservationService.releaseReservation(driverUserId, session.get().getDispatchId().toString());
    }
}
