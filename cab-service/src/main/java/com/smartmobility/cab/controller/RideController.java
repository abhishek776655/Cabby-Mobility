package com.smartmobility.cab.controller;


import com.smartmobility.cab.dto.*;
import com.smartmobility.cab.service.RideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/rides")
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService;

    @PostMapping
    public ResponseEntity<ApiResponse<RideResponseDTO>> createRide(
            @RequestHeader("X-User-Id") Long currentUserId,
            @Valid @RequestBody RideRequestDTO request) {

        return ResponseEntity.ok(
                ApiResponseBuilder.success(
                        rideService.createRide(request, currentUserId),
                        "Ride created successfully"
                )
        );
    }

    @GetMapping("/{rideId}")
    public ResponseEntity<ApiResponse<RideResponseDTO>> getRide(
            @RequestHeader("X-User-Id") Long currentUserId,
            @PathVariable UUID rideId) {

        return ResponseEntity.ok(
                ApiResponseBuilder.success(
                        rideService.getRideById(rideId, currentUserId),
                        "Ride fetched successfully"
                )
        );
    }

    @PostMapping("/{rideId}/retry")
    public ResponseEntity<ApiResponse<RideResponseDTO>> retryMatch(
            @RequestHeader("X-User-Id") Long currentUserId,
            @PathVariable UUID rideId) {

        return ResponseEntity.ok(
                ApiResponseBuilder.success(
                        rideService.retryMatch(rideId, currentUserId),
                        "Searching for a driver again"
                )
        );
    }

    @PostMapping("/{rideId}/cancel")
    public ResponseEntity<ApiResponse<RideResponseDTO>> cancelRide(
            @RequestHeader("X-User-Id") Long currentUserId,
            @PathVariable UUID rideId) {

        return ResponseEntity.ok(
                ApiResponseBuilder.success(
                        rideService.cancelRide(rideId, currentUserId),
                        "Ride cancelled successfully"
                )
        );
    }

    @PostMapping("/{rideId}/start")
    public ResponseEntity<ApiResponse<RideResponseDTO>> startRide(
            @RequestHeader("X-User-Id") Long currentUserId,
            @PathVariable UUID rideId) {

        return ResponseEntity.ok(
                ApiResponseBuilder.success(
                        rideService.startRide(rideId, currentUserId),
                        "Ride started successfully"
                )
        );
    }

    @PostMapping("/{rideId}/complete")
    public ResponseEntity<ApiResponse<RideResponseDTO>> completeRide(
            @RequestHeader("X-User-Id") Long currentUserId,
            @PathVariable UUID rideId) {

        return ResponseEntity.ok(
                ApiResponseBuilder.success(
                        rideService.completeRide(rideId, currentUserId),
                        "Ride completed successfully"
                )
        );
    }
    @PostMapping("/{id}/match")
    public ResponseEntity<ApiResponse<RideResponseDTO>> match(
            @RequestHeader(value = "X-User-Role", required = false) String rolesHeader,
            @PathVariable UUID id) {

        if (rolesHeader == null || !rolesHeader.contains("ADMIN")) {
            throw new com.smartmobility.cab.exception.ForbiddenAccessException("Admin access required");
        }

        return ResponseEntity.ok(
                ApiResponseBuilder.success(
                        rideService.matchRide(id), "Matching started"
                )
        );
    }


}
