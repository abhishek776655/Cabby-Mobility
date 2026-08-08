package com.smartmobility.driver_service.controller;

import com.smartmobility.driver_service.dto.ApiResponse;
import com.smartmobility.driver_service.dto.ApiResponseBuilder;
import com.smartmobility.driver_service.dto.CreateDriverRequestDTO;
import com.smartmobility.driver_service.dto.DriverResponseDTO;
import com.smartmobility.driver_service.security.DriverAuthorizationGuard;
import com.smartmobility.driver_service.service.DriverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;
    private final DriverAuthorizationGuard authorizationGuard;

    // ✅ Create driver
    @PostMapping
    public ResponseEntity<ApiResponse<DriverResponseDTO>> createDriver(
            @RequestHeader("X-User-Id") Long currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String rolesHeader,
            @Valid @RequestBody CreateDriverRequestDTO request
    ) {
        authorizationGuard.assertSelfOrAdmin(request.getUserId(), currentUserId, rolesHeader);
        DriverResponseDTO response = driverService.createDriver(request);

        return ResponseEntity.ok(ApiResponseBuilder.success(response, "Driver Created Successfully"));
    }

    // ✅ Get driver
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<DriverResponseDTO>> getDriver(
            @RequestHeader("X-User-Id") Long currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String rolesHeader,
            @PathVariable Long userId
    ) {
        authorizationGuard.assertSelfOrAdmin(userId, currentUserId, rolesHeader);
        DriverResponseDTO response = driverService.getDriver(userId);

        return ResponseEntity.ok(ApiResponseBuilder.success(response, "Found Driver"));
    }

    // ✅ Internal Get driver
    @GetMapping("/internal/{userId}")
    public ResponseEntity<ApiResponse<DriverResponseDTO>> getDriverInternal(
            @PathVariable Long userId
    ) {
        DriverResponseDTO response = driverService.getDriver(userId);
        return ResponseEntity.ok(ApiResponseBuilder.success(response, "Found Driver"));
    }

    // ✅ Internal update availability
    @PatchMapping("/internal/{userId}/availability")
    public ResponseEntity<ApiResponse<DriverResponseDTO>> updateAvailabilityInternal(
            @PathVariable Long userId,
            @RequestParam Boolean available
    ) {
        DriverResponseDTO response = driverService.updateAvailability(userId, available);
        return ResponseEntity.ok(ApiResponseBuilder.success(response, "Driver Available"));
    }

    // ✅ Get drivers batch
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<DriverResponseDTO>>> getDriversBatch(
            @RequestBody List<Long> userIds
    ) {
        List<DriverResponseDTO> response = driverService.getDriversBatch(userIds);
        return ResponseEntity.ok(ApiResponseBuilder.success(response, "Found Drivers"));
    }

    // ✅ Update availability
    @PatchMapping("/{userId}/availability")
    public ResponseEntity<ApiResponse<DriverResponseDTO>> updateAvailability(
            @RequestHeader("X-User-Id") Long currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String rolesHeader,
            @PathVariable Long userId,
            @RequestParam Boolean available
    ) {
        authorizationGuard.assertSelfOrAdmin(userId, currentUserId, rolesHeader);
        DriverResponseDTO response = driverService.updateAvailability(userId, available);

        return ResponseEntity.ok(ApiResponseBuilder.success(response, "Driver Available"));
    }
}
