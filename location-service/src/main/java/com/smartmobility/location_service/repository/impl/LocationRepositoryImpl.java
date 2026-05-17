package com.smartmobility.location_service.repository.impl;

import com.smartmobility.location_service.constants.RedisKeys;
import com.smartmobility.location_service.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class LocationRepositoryImpl implements LocationRepository {

    private final GeoOperations<String, String> geoOps;
    private final GeoOperations<String, String> availableGeoOps;
    private final SetOperations<String, String> setOps;

    // 1️⃣ Upsert location
    @Override
    public void upsertDriverLocation(String driverUserId, double lat, double lng) {
        Point point = new Point(lng, lat);
        geoOps.add(RedisKeys.DRIVERS_GEO, point, driverUserId);
        if (Boolean.TRUE.equals(setOps.isMember(RedisKeys.DRIVERS_AVAILABLE, driverUserId))) {
            availableGeoOps.add(RedisKeys.DRIVERS_AVAILABLE_GEO, point, driverUserId);
        }
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

        return results.getContent().stream()
                .map(r -> r.getContent().getName())
                .collect(Collectors.toList());
    }
}