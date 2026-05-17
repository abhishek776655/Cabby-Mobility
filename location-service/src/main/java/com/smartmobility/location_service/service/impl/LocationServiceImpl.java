package com.smartmobility.location_service.service.impl;

import com.smartmobility.location_service.exception.InvalidLocationException;
import com.smartmobility.location_service.exception.LocationServiceException;
import com.smartmobility.location_service.repository.LocationRepository;
import com.smartmobility.location_service.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private static final double MAX_RADIUS_KM = 50.0;
    private static final int MAX_LIMIT = 50;

    private final LocationRepository locationRepository;

    @Override
    public void goOnline(Long driverUserId, double lat, double lng) {
        validateDriverUserId(driverUserId);
        validateCoordinates(lat, lng);

        try {
            String driverUserIdKey = driverUserId.toString();
            locationRepository.upsertDriverLocation(driverUserIdKey, lat, lng);
            locationRepository.markDriverOnline(driverUserIdKey);
        } catch (RuntimeException ex) {
            throw new LocationServiceException("Failed to mark driver online", ex);
        }
    }

    @Override
    public void goOffline(Long driverUserId) {
        validateDriverUserId(driverUserId);

        try {
            locationRepository.markDriverOffline(driverUserId.toString());
        } catch (RuntimeException ex) {
            throw new LocationServiceException("Failed to mark driver offline", ex);
        }
    }

    @Override
    public void updateDriverLocation(Long driverUserId, double lat, double lng) {
        validateDriverUserId(driverUserId);
        validateCoordinates(lat, lng);

        try {
            locationRepository.upsertDriverLocation(driverUserId.toString(), lat, lng);
        } catch (RuntimeException ex) {
            throw new LocationServiceException("Failed to update driver location", ex);
        }
    }

    @Override
    public List<Long> getNearbyDrivers(double lat, double lng, double radiusKm, int limit) {
        validateCoordinates(lat, lng);
        validateSearch(radiusKm, limit);

        try {
            return locationRepository.findNearbyDrivers(lat, lng, radiusKm, limit).stream()
                    .map(Long::valueOf)
                    .toList();
        } catch (RuntimeException ex) {
            throw new LocationServiceException("Failed to find nearby drivers", ex);
        }
    }

    private void validateDriverUserId(Long driverUserId) {
        if (driverUserId == null) {
            throw new InvalidLocationException("driverUserId is required");
        }
    }

    private void validateCoordinates(double lat, double lng) {
        if (lat < -90.0 || lat > 90.0) {
            throw new InvalidLocationException("Latitude must be between -90 and 90");
        }
        if (lng < -180.0 || lng > 180.0) {
            throw new InvalidLocationException("Longitude must be between -180 and 180");
        }
    }

    private void validateSearch(double radiusKm, int limit) {
        if (radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            throw new InvalidLocationException("radiusKm must be between 0 and 50");
        }
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new InvalidLocationException("limit must be between 1 and 50");
        }
    }
}
