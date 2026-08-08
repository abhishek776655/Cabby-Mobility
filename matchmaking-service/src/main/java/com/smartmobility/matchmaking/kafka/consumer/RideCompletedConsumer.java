package com.smartmobility.matchmaking.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmobility.matchmaking.entity.DispatchSessionEntity;
import com.smartmobility.matchmaking.event.RideCompletedEvent;
import com.smartmobility.matchmaking.redis.ReservationService;
import com.smartmobility.matchmaking.repository.DispatchSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Releases the driver's on-trip reservation once the ride they were carrying actually
 * completes. Without this, a driver's reservation was only ever released on acceptance,
 * making them falsely available to other rides for the entire trip.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideCompletedConsumer {

    private final DispatchSessionRepository dispatchRepository;
    private final ReservationService reservationService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @KafkaListener(
        topics = "ride-completed",
        groupId = "matchmaking-service-group"
    )
    public void consume(String message) {
        log.info("Received RideCompleted: {}", message);
        try {
            RideCompletedEvent event = objectMapper.readValue(message, RideCompletedEvent.class);

            String redisKey = "processed:" + event.getEventId();
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", 24, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                log.info("Event {} already processed, skipping duplicate delivery.", event.getEventId());
                return;
            }

            releaseDriverForRide(event.getRideId(), event.getDriverUserId());
        } catch (Exception e) {
            log.error("Failed to process RideCompleted: {}", message, e);
            throw new RuntimeException("Failed to process RideCompleted", e);
        }
    }

    private void releaseDriverForRide(java.util.UUID rideId, Long driverUserId) {
        if (driverUserId == null) {
            return;
        }

        Optional<DispatchSessionEntity> session = dispatchRepository.findByRideId(rideId);
        if (session.isEmpty()) {
            log.warn("No dispatch session found for completed ride {}, cannot release driver {}",
                    rideId, driverUserId);
            return;
        }

        reservationService.releaseReservation(driverUserId, session.get().getDispatchId().toString());
    }
}
