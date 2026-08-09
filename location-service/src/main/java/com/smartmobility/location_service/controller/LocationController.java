package com.smartmobility.location_service.controller;

import com.smartmobility.location_service.dto.ApiResponse;
import com.smartmobility.location_service.dto.ApiResponseBuilder;
import com.smartmobility.location_service.dto.NearbyDriversRequest;
import com.smartmobility.location_service.dto.UpdateLocationRequest;
import com.smartmobility.location_service.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    // 1️⃣ Driver goes ONLINE
    @PostMapping("/location/driver/online")
    public ResponseEntity<ApiResponse<Void>> goOnline(
            @RequestHeader("X-User-Id") Long currentUserId,
            @Valid @RequestBody UpdateLocationRequest request) {

        locationService.goOnline(
                request.getDriverUserId(),
                currentUserId,
                request.getLat(),
                request.getLng()
        );

        return ResponseEntity.ok(ApiResponseBuilder.success(null, "Driver is online"));
    }

    // 2️⃣ Driver goes OFFLINE
    @PostMapping("/location/driver/offline")
    public ResponseEntity<ApiResponse<Void>> goOffline(
            @RequestHeader("X-User-Id") Long currentUserId,
            @RequestParam Long driverUserId) {

        locationService.goOffline(driverUserId, currentUserId);

        return ResponseEntity.ok(ApiResponseBuilder.success(null, "Driver is offline"));
    }

    // 3️⃣ Location update
    @PostMapping("/location/driver/update")
    public ResponseEntity<ApiResponse<Void>> updateLocation(
            @RequestHeader("X-User-Id") Long currentUserId,
            @Valid @RequestBody UpdateLocationRequest request) {

        locationService.updateDriverLocation(
                request.getDriverUserId(),
                currentUserId,
                request.getLat(),
                request.getLng()
        );

        return ResponseEntity.ok(ApiResponseBuilder.success(null, "Location updated"));
    }

    // 4️⃣ Nearby drivers (INTERNAL ONLY - for matchmaking service)
    @PostMapping("/internal/nearby")
    public ResponseEntity<ApiResponse<List<Long>>> getNearbyDrivers(
            @Valid @RequestBody NearbyDriversRequest request) {

        List<Long> drivers = locationService.getNearbyDrivers(
                request.getLat(),
                request.getLng(),
                request.getRadiusKm(),
                request.getLimit()
        );

        return ResponseEntity.ok(ApiResponseBuilder.success(drivers, "Nearby drivers found"));
    }

    // 5️⃣ Batch driver locations (INTERNAL ONLY)
    @PostMapping("/internal/locations/batch")
    public ResponseEntity<ApiResponse<List<com.smartmobility.location_service.dto.DriverLocationDTO>>> getDriverLocations(
            @RequestBody List<Long> driverUserIds) {
        
        List<com.smartmobility.location_service.dto.DriverLocationDTO> locations = locationService.getDriverLocations(driverUserIds);
        return ResponseEntity.ok(ApiResponseBuilder.success(locations, "Locations retrieved"));
    }
}
