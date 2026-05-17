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
@RequestMapping("/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    // 1️⃣ Driver goes ONLINE
    @PostMapping("/driver/online")
    public ResponseEntity<ApiResponse<Void>> goOnline(
            @Valid @RequestBody UpdateLocationRequest request) {

        locationService.goOnline(
                request.getDriverUserId(),
                request.getLat(),
                request.getLng()
        );

        return ResponseEntity.ok(ApiResponseBuilder.success(null, "Driver is online"));
    }

    // 2️⃣ Driver goes OFFLINE
    @PostMapping("/driver/offline")
    public ResponseEntity<ApiResponse<Void>> goOffline(
            @RequestParam Long driverUserId) {

        locationService.goOffline(driverUserId);

        return ResponseEntity.ok(ApiResponseBuilder.success(null, "Driver is offline"));
    }

    // 3️⃣ Location update
    @PostMapping("/driver/update")
    public ResponseEntity<ApiResponse<Void>> updateLocation(
            @Valid @RequestBody UpdateLocationRequest request) {

        locationService.updateDriverLocation(
                request.getDriverUserId(),
                request.getLat(),
                request.getLng()
        );

        return ResponseEntity.ok(ApiResponseBuilder.success(null, "Location updated"));
    }

    // 4️⃣ Nearby drivers (INTERNAL ONLY - for matchmaking service)
    @PostMapping("/nearby")
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
}
