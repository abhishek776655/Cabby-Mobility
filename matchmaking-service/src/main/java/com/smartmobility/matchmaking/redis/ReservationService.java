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
        int ttlSeconds = properties.getDispatchTimeoutSeconds();

        Boolean result = redisTemplate.opsForValue()
            .setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds));

        log.info("Reservation attempt for driver {}: {}", driverUserId, result);
        return Boolean.TRUE.equals(result);
    }

    /**
     * Prolongs an existing reservation's TTL without changing its owner. Used when a driver
     * accepts a ride: the offer-window reservation must not be released (that would make the
     * driver look available to other rides while actually on-trip), but should instead cover
     * the ride's expected duration as a safety net until the ride-completed/cancelled event
     * explicitly releases it.
     */
    public boolean extendReservation(Long driverUserId, String dispatchId, long ttlSeconds) {
        String key = String.format(RESERVATION_KEY_PREFIX, driverUserId);
        String currentValue = redisTemplate.opsForValue().get(key);

        if (currentValue == null || !currentValue.startsWith(dispatchId + ":")) {
            log.warn("Cannot extend reservation for driver {}: no matching reservation for dispatch {}",
                    driverUserId, dispatchId);
            return false;
        }

        Boolean result = redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
        log.info("Extended reservation for driver {} to {}s: {}", driverUserId, ttlSeconds, result);
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

    // KEYS is O(n) over the driver-reservation keyspace; acceptable at current fleet size.
    // A counter incremented/decremented on acquire/release would drift on TTL expiry (a
    // reservation lapsing doesn't fire a release call), so a live scan stays correct instead.
    public long countActiveReservations() {
        return redisTemplate.keys(RESERVATION_KEY_PREFIX.replace("%s", "*")).size();
    }
}
