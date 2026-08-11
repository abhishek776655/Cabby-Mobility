package com.smartmobility.routing_service.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmobility.routing_service.dto.RouteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "route:";
    private static final Duration TTL = Duration.ofMinutes(5);

    public Optional<RouteResponse> get(double originLat, double originLng, double destLat, double destLng, String costingModel) {
        String key = buildKey(originLat, originLng, destLat, destLng, costingModel);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return Optional.of(objectMapper.readValue(json, RouteResponse.class));
            }
        } catch (Exception e) {
            log.warn("Failed to read route cache for key {}, falling back to Valhalla", key, e);
        }
        return Optional.empty();
    }

    public void put(double originLat, double originLng, double destLat, double destLng, String costingModel, RouteResponse response) {
        String key = buildKey(originLat, originLng, destLat, destLng, costingModel);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(response), TTL);
        } catch (Exception e) {
            log.warn("Failed to write route cache for key {}", key, e);
        }
    }

    // 5 decimal places ~= 1.1m precision — GPS jitter within that range still hits the cache.
    private String buildKey(double originLat, double originLng, double destLat, double destLng, String costingModel) {
        return String.format(Locale.US, "%s%.5f,%.5f-%.5f,%.5f-%s",
            KEY_PREFIX, originLat, originLng, destLat, destLng, costingModel);
    }
}
