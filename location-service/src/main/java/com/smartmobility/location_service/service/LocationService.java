package com.smartmobility.location_service.service;

import java.util.List;

public interface LocationService {

    void goOnline(Long driverUserId, Long currentUserId, double lat, double lng);

    void goOffline(Long driverUserId, Long currentUserId);

    void updateDriverLocation(Long driverUserId, Long currentUserId, double lat, double lng);

    List<Long> getNearbyDrivers(double lat, double lng, double radiusKm, int limit);
}
