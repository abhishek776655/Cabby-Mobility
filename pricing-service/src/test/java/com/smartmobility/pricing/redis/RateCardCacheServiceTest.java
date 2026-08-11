package com.smartmobility.pricing.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartmobility.pricing.entity.RateCardEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateCardCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateCardCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new RateCardCacheService(redisTemplate, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void getReturnsEmptyOnCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ratecard:STANDARD")).thenReturn(null);

        assertTrue(cacheService.get("STANDARD").isEmpty());
    }

    @Test
    void putThenGetRoundTripsTheRateCard() throws Exception {
        RateCardEntity rateCard = RateCardEntity.builder()
            .vehicleType("STANDARD").baseFare(5000).perKmRate(1500).perMinRate(200)
            .minFare(8000).cancellationFee(3000).active(true).updatedAt(LocalDateTime.now())
            .build();
        ObjectMapper realMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = realMapper.writeValueAsString(rateCard);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ratecard:STANDARD")).thenReturn(json);

        Optional<RateCardEntity> result = cacheService.get("STANDARD");

        assertTrue(result.isPresent());
        assertEquals("STANDARD", result.get().getVehicleType());
        assertEquals(5000, result.get().getBaseFare());
    }

    @Test
    void getReturnsEmptyWhenRedisThrows() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis unavailable"));

        assertTrue(cacheService.get("STANDARD").isEmpty());
    }
}
