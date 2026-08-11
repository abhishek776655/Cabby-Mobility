package com.smartmobility.pricing.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmobility.pricing.entity.RateCardEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateCardCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "ratecard:";
    private static final Duration TTL = Duration.ofMinutes(5);

    public Optional<RateCardEntity> get(String vehicleType) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + vehicleType);
            if (json != null) {
                return Optional.of(objectMapper.readValue(json, RateCardEntity.class));
            }
        } catch (Exception e) {
            log.warn("Failed to read rate card cache for {}, falling back to DB", vehicleType, e);
        }
        return Optional.empty();
    }

    public void put(String vehicleType, RateCardEntity rateCard) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + vehicleType, objectMapper.writeValueAsString(rateCard), TTL);
        } catch (Exception e) {
            log.warn("Failed to write rate card cache for {}", vehicleType, e);
        }
    }
}
