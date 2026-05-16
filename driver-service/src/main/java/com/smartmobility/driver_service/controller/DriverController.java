package com.smartmobility.driver_service.controller;

import com.smartmobility.driver_service.dto.ApiResponse;
import com.smartmobility.driver_service.dto.ApiResponseBuilder;
import com.smartmobility.driver_service.dto.CreateDriverRequestDTO;
import com.smartmobility.driver_service.dto.DriverResponseDTO;
import com.smartmobility.driver_service.service.DriverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    // ✅ Create driver
    @PostMapping
    public ResponseEntity<ApiResponse<DriverResponseDTO>> createDriver(
            @Valid @RequestBody CreateDriverRequestDTO request
    ) {
        DriverResponseDTO response = driverService.createDriver(request);

        return ResponseEntity.ok(ApiResponseBuilder.success(response, "Driver Created Successfully"));
    }

    // ✅ Get driver
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<DriverResponseDTO>> getDriver(
            @PathVariable Long userId
    ) {
        DriverResponseDTO response = driverService.getDriver(userId);

        return ResponseEntity.ok(ApiResponseBuilder.success(response, "Found Driver"));
    }

    // ✅ Update availability
    @PatchMapping("/{userId}/availability")
    public ResponseEntity<ApiResponse<DriverResponseDTO>> updateAvailability(
            @PathVariable Long userId,
            @RequestParam Boolean available
    ) {
        DriverResponseDTO response = driverService.updateAvailability(userId, available);

        return ResponseEntity.ok(ApiResponseBuilder.success(response, "Driver Available"));
    }
}
