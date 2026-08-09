package com.smartmobility.pricing.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class SurgeCacheService {

    private final StringRedisTemplate redisTemplate;

    private static final String SURGE_KEY_PREFIX = "surge:";
    private static final double DEFAULT_SURGE = 1.0;

    /**
     * Get the current surge multiplier for a given zone.
     * @param zoneId the zone identifier
     * @return surge multiplier, defaults to 1.0 if not found
     */
    public double getSurgeMultiplier(String zoneId) {
        String key = SURGE_KEY_PREFIX + zoneId;
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return Double.parseDouble(value);
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve surge multiplier from Redis for zone {}, defaulting to 1.0", zoneId, e);
        }
        return DEFAULT_SURGE;
    }

    /**
     * Ops-only method to update surge multiplier.
     * @param zoneId the zone identifier
     * @param multiplier the new multiplier
     * @param ttlSeconds time to live
     */
    public void setSurgeMultiplier(String zoneId, double multiplier, long ttlSeconds) {
        String key = SURGE_KEY_PREFIX + zoneId;
        try {
            redisTemplate.opsForValue().set(key, String.valueOf(multiplier), Duration.ofSeconds(ttlSeconds));
            log.info("Set surge multiplier for zone {} to {}", zoneId, multiplier);
        } catch (Exception e) {
            log.error("Failed to set surge multiplier in Redis for zone {}", zoneId, e);
            throw new RuntimeException("Redis unavailable", e);
        }
    }
}
