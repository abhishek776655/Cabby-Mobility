package com.smartmobility.location_service.repository;

import java.util.List;

public interface LocationRepository {

    void upsertDriverLocation(String driverUserId, double lat, double lng);

    void markDriverOnline(String driverUserId);

    void markDriverOffline(String driverUserId);

    List<String> findNearbyDrivers(double lat, double lng, double radiusKm, int limit);
}