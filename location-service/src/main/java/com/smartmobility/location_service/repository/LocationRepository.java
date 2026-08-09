package com.smartmobility.location_service.repository;

import java.util.List;

public interface LocationRepository {

    void upsertDriverLocation(String driverUserId, double lat, double lng);

    void markDriverOnline(String driverUserId);

    void markDriverOffline(String driverUserId);

    Long countOnlineDrivers();

    List<String> findNearbyDrivers(double lat, double lng, double radiusKm, int limit);

    List<org.springframework.data.geo.Point> getDriverLocations(List<String> driverUserIds);

    /**
     * Removes drivers with no active heartbeat (driver:active:* TTL expired) from the
     * geo/available sets so a crashed driver that never called offline doesn't linger forever.
     */
    void evictStaleDrivers();
}