package com.smartmobility.matchmaking.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchCacheService {

    private final StringRedisTemplate redisTemplate;
    private static final String DISPATCH_KEY_PREFIX = "dispatch:%s";

    public void saveDispatchState(String dispatchId, String status, Long driverId, long expiresAtEpoch) {
        String key = String.format(DISPATCH_KEY_PREFIX, dispatchId);
        Map<String, String> values = Map.of(
            "status", status,
            "driverId", driverId != null ? driverId.toString() : "",
            "expiresAt", String.valueOf(expiresAtEpoch)
        );
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, Duration.ofMinutes(5));
    }

    public Optional<Map<Object, Object>> getDispatchState(String dispatchId) {
        String key = String.format(DISPATCH_KEY_PREFIX, dispatchId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries);
    }

    public void deleteDispatchState(String dispatchId) {
        String key = String.format(DISPATCH_KEY_PREFIX, dispatchId);
        redisTemplate.delete(key);
    }
}