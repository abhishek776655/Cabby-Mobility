package com.smartmobility.location_service.service;

import java.util.List;

public interface LocationService {

    void goOnline(Long driverUserId, double lat, double lng);

    void goOffline(Long driverUserId);

    void updateDriverLocation(Long driverUserId, double lat, double lng);

    List<Long> getNearbyDrivers(double lat, double lng, double radiusKm, int limit);
}
