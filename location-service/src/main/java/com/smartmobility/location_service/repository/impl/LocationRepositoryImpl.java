package com.smartmobility.location_service.repository.impl;

import com.smartmobility.location_service.constants.RedisKeys;
import com.smartmobility.location_service.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class LocationRepositoryImpl implements LocationRepository {

    private final GeoOperations<String, String> geoOps;
    private final GeoOperations<String, String> availableGeoOps;
    private final SetOperations<String, String> setOps;
    private final ValueOperations<String, String> valueOps;
    private final ZSetOperations<String, String> zSetOps;

    // 1️⃣ Upsert location
    @Override
    public void upsertDriverLocation(String driverUserId, double lat, double lng) {
        Point point = new Point(lng, lat);
        geoOps.add(RedisKeys.DRIVERS_GEO, point, driverUserId);
        if (Boolean.TRUE.equals(setOps.isMember(RedisKeys.DRIVERS_AVAILABLE, driverUserId))) {
            availableGeoOps.add(RedisKeys.DRIVERS_AVAILABLE_GEO, point, driverUserId);
        }
        valueOps.set("driver:active:" + driverUserId, "1", java.time.Duration.ofSeconds(60));
    }

    // 2️⃣ Mark ONLINE
    @Override
    public void markDriverOnline(String driverUserId) {
        setOps.add(RedisKeys.DRIVERS_AVAILABLE, driverUserId);
        List<Point> points = geoOps.position(RedisKeys.DRIVERS_GEO, driverUserId);
        if (points != null && !points.isEmpty()) {
            availableGeoOps.add(RedisKeys.DRIVERS_AVAILABLE_GEO, points.get(0), driverUserId);
        }
    }

    // 3️⃣ Mark OFFLINE
    @Override
    public void markDriverOffline(String driverUserId) {
        setOps.remove(RedisKeys.DRIVERS_AVAILABLE, driverUserId);
        availableGeoOps.remove(RedisKeys.DRIVERS_AVAILABLE_GEO, driverUserId);
    }
    
    @Override
    public Long countOnlineDrivers() {
        return setOps.size(RedisKeys.DRIVERS_AVAILABLE);
    }

    // 4️⃣ Find nearby drivers
    @Override
    public List<String> findNearbyDrivers(double lat, double lng, double radiusKm, int limit) {
        Point center = new Point(lng, lat);
        Distance radius = new Distance(radiusKm, Metrics.KILOMETERS);

        RedisGeoCommands.GeoSearchCommandArgs args = RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                .includeDistance()
                .sortAscending()
                .limit(limit);

        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                availableGeoOps.search(RedisKeys.DRIVERS_AVAILABLE_GEO,
                        GeoReference.fromCoordinate(center),
                        radius,
                        args);

        if (results == null) return Collections.emptyList();

        List<String> activeDrivers = new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results.getContent()) {
            String driverId = result.getContent().getName();
            if (valueOps.get("driver:active:" + driverId) != null) {
                activeDrivers.add(driverId);
            } else {
                availableGeoOps.remove(RedisKeys.DRIVERS_AVAILABLE_GEO, driverId);
                setOps.remove(RedisKeys.DRIVERS_AVAILABLE, driverId);
            }
        }

        return activeDrivers;
    }

    @Override
    public List<Point> getDriverLocations(List<String> driverUserIds) {
        if (driverUserIds == null || driverUserIds.isEmpty()) return Collections.emptyList();
        String[] members = driverUserIds.toArray(new String[0]);
        return geoOps.position(RedisKeys.DRIVERS_GEO, members);
    }

    // 5️⃣ Reap drivers whose heartbeat TTL has expired without ever calling offline
    @Override
    public void evictStaleDrivers() {
        Set<String> driverIds = zSetOps.range(RedisKeys.DRIVERS_GEO, 0, -1);
        if (driverIds == null || driverIds.isEmpty()) {
            return;
        }

        int evicted = 0;
        for (String driverId : driverIds) {
            if (valueOps.get("driver:active:" + driverId) == null) {
                geoOps.remove(RedisKeys.DRIVERS_GEO, driverId);
                availableGeoOps.remove(RedisKeys.DRIVERS_AVAILABLE_GEO, driverId);
                setOps.remove(RedisKeys.DRIVERS_AVAILABLE, driverId);
                evicted++;
            }
        }
        if (evicted > 0) {
            log.info("Evicted {} stale driver(s) with no active heartbeat", evicted);
        }
    }
}