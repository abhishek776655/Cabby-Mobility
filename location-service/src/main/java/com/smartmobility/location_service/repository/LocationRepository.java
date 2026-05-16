package com.smartmobility.location_service.repository;

import java.util.List;

public interface LocationRepository {

    void upsertDriverLocation(String driverId, double lat, double lng);

    void markDriverOnline(String driverId);

    void markDriverOffline(String driverId);

    List<String> findNearbyDrivers(double lat, double lng, double radiusKm, int limit);
}