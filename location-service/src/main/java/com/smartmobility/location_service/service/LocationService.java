package com.smartmobility.location_service.service;

import java.util.List;

public interface LocationService {

    void goOnline(String driverId, double lat, double lng);

    void goOffline(String driverId);

    void updateDriverLocation(String driverId, double lat, double lng);

    List<String> getNearbyDrivers(double lat, double lng, double radiusKm, int limit);
}
