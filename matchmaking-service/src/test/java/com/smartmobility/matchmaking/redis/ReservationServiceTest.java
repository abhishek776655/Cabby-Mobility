package com.smartmobility.matchmaking.redis;

import com.smartmobility.matchmaking.config.MatchmakingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private MatchmakingProperties properties;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        properties = new MatchmakingProperties();
        properties.getReservation().setTtlSeconds(15);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        service = new ReservationService(redisTemplate, properties);
    }

    @Test
    void testAcquireReservationSuccess() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);

        boolean result = service.acquireReservation(1L, "dispatch-123", "ride-456");

        assertTrue(result);
        verify(valueOps).setIfAbsent(
            eq("driver:1:reservation"),
            eq("dispatch-123:ride-456"),
            eq(Duration.ofSeconds(15))
        );
    }

    @Test
    void testAcquireReservationFailure() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(false);

        boolean result = service.acquireReservation(1L, "dispatch-123", "ride-456");

        assertFalse(result);
    }

    @Test
    void testReleaseReservationSuccess() {
        when(valueOps.get("driver:1:reservation"))
            .thenReturn("dispatch-123:ride-456");
        when(redisTemplate.delete("driver:1:reservation"))
            .thenReturn(true);

        boolean result = service.releaseReservation(1L, "dispatch-123");

        assertTrue(result);
    }

    @Test
    void testReleaseReservationNotOwner() {
        when(valueOps.get("driver:1:reservation"))
            .thenReturn("dispatch-other:ride-456");

        boolean result = service.releaseReservation(1L, "dispatch-123");

        assertFalse(result);
    }

    @Test
    void testHasActiveReservation() {
        when(valueOps.get("driver:1:reservation"))
            .thenReturn("dispatch-123:ride-456");

        assertTrue(service.hasActiveReservation(1L));
    }

    @Test
    void testNoActiveReservation() {
        when(valueOps.get("driver:1:reservation"))
            .thenReturn(null);

        assertFalse(service.hasActiveReservation(1L));
    }
}