package com.smartmobility.routing_service.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmobility.routing_service.dto.RouteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RouteCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new RouteCacheService(redisTemplate, new ObjectMapper());
    }

    @Test
    void getReturnsEmptyOnCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertTrue(cacheService.get(12.97160, 77.59460, 12.93520, 77.62440, "auto").isEmpty());
    }

    @Test
    void putThenGetRoundTripsTheRoute() {
        RouteResponse response = RouteResponse.builder()
            .polyline("abc123").coordinates(List.of())
            .distanceMeters(4600.0).durationSeconds(980.0)
            .legs(List.of())
            .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService.put(12.97160, 77.59460, 12.93520, 77.62440, "auto", response);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), eq(Duration.ofMinutes(5)));

        when(valueOperations.get(keyCaptor.getValue())).thenReturn(valueCaptor.getValue());

        Optional<RouteResponse> cached = cacheService.get(12.97160, 77.59460, 12.93520, 77.62440, "auto");
        assertTrue(cached.isPresent());
        assertEquals("abc123", cached.get().getPolyline());
        assertEquals(4600.0, cached.get().getDistanceMeters());
    }

    @Test
    void roundedCoordinatesProduceTheSameKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService.get(12.971601, 77.594601, 12.935201, 77.624401, "auto");
        cacheService.get(12.971604, 77.594604, 12.935204, 77.624404, "auto");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(2)).get(captor.capture());
        assertEquals(captor.getAllValues().get(0), captor.getAllValues().get(1));
    }
}
