package com.smartmobility.location_service.repository.impl;

import com.smartmobility.location_service.constants.RedisKeys;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LocationRepositoryImplTest {

    private final GeoOperations<String, String> geoOps = mock(GeoOperations.class);
    private final GeoOperations<String, String> availableGeoOps = mock(GeoOperations.class);
    private final SetOperations<String, String> setOps = mock(SetOperations.class);
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);

    private final LocationRepositoryImpl repository =
            new LocationRepositoryImpl(geoOps, availableGeoOps, setOps, valueOps, zSetOps);

    @Test
    void evictStaleDriversRemovesDriversWithoutActiveHeartbeat() {
        when(zSetOps.range(RedisKeys.DRIVERS_GEO, 0, -1)).thenReturn(Set.of("stale-1", "fresh-1"));
        when(valueOps.get("driver:active:stale-1")).thenReturn(null);
        when(valueOps.get("driver:active:fresh-1")).thenReturn("1");

        repository.evictStaleDrivers();

        verify(geoOps).remove(RedisKeys.DRIVERS_GEO, "stale-1");
        verify(availableGeoOps).remove(RedisKeys.DRIVERS_AVAILABLE_GEO, "stale-1");
        verify(setOps).remove(eq(RedisKeys.DRIVERS_AVAILABLE), eq("stale-1"));

        verify(geoOps, never()).remove(RedisKeys.DRIVERS_GEO, "fresh-1");
        verify(availableGeoOps, never()).remove(RedisKeys.DRIVERS_AVAILABLE_GEO, "fresh-1");
    }

    @Test
    void evictStaleDriversNoOpWhenGeoSetEmpty() {
        when(zSetOps.range(RedisKeys.DRIVERS_GEO, 0, -1)).thenReturn(Set.of());

        repository.evictStaleDrivers();

        verifyNoInteractions(valueOps);
    }
}
