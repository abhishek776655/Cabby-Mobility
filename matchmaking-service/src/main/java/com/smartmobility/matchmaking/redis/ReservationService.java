package com.smartmobility.matchmaking.redis;

import com.smartmobility.matchmaking.config.MatchmakingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final StringRedisTemplate redisTemplate;
    private final MatchmakingProperties properties;

    private static final String RESERVATION_KEY_PREFIX = "driver:%s:reservation";

    public boolean acquireReservation(Long driverUserId, String dispatchId, String rideId) {
        String key = String.format(RESERVATION_KEY_PREFIX, driverUserId);
        String value = dispatchId + ":" + rideId;
        int ttlSeconds = properties.getReservation().getTtlSeconds();

        Boolean result = redisTemplate.opsForValue()
            .setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds));

        log.info("Reservation attempt for driver {}: {}", driverUserId, result);
        return Boolean.TRUE.equals(result);
    }

    public boolean releaseReservation(Long driverUserId, String dispatchId) {
        String key = String.format(RESERVATION_KEY_PREFIX, driverUserId);
        String currentValue = redisTemplate.opsForValue().get(key);

        if (currentValue == null) {
            return false;
        }

        if (currentValue.startsWith(dispatchId + ":")) {
            Boolean deleted = redisTemplate.delete(key);
            log.info("Released reservation for driver {}: {}", driverUserId, deleted);
            return Boolean.TRUE.equals(deleted);
        }
        return false;
    }

    public Optional<String> getReservation(Long driverUserId) {
        String key = String.format(RESERVATION_KEY_PREFIX, driverUserId);
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public boolean hasActiveReservation(Long driverUserId) {
        return getReservation(driverUserId).isPresent();
    }
}