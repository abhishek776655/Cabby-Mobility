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
            @Valid @RequestBody RideRequestDTO request) {

        return ResponseEntity.ok(
                ApiResponseBuilder.success(
                        rideService.createRide(request),
                        "Ride created successfully"
                )
        );
    }

    @GetMapping("/{rideId}")
    public ResponseEntity<ApiResponse<RideResponseDTO>> getRide(
            @PathVariable UUID rideId) {

        return ResponseEntity.ok(
                ApiResponseBuilder.success(
                        rideService.getRideById(rideId),
                        "Ride fetched successfully"
                )
        );
    }

    @PostMapping("/{rideId}/cancel")
    public ResponseEntity<ApiResponse<RideResponseDTO>> cancelRide(
            @PathVariable UUID rideId) {

        return ResponseEntity.ok(
                ApiResponseBuilder.success(
                        rideService.cancelRide(rideId),
                        "Ride cancelled successfully"
                )
        );
    }

    @PostMapping("/{rideId}/start")
    public ResponseEntity<ApiResponse<RideResponseDTO>> startRide(
            @PathVariable UUID rideId) {

        return ResponseEntity.ok(
                ApiResponseBuilder.success(
                        rideService.startRide(rideId),
                        "Ride started successfully"
                )
        );
    }

    @PostMapping("/{rideId}/complete")
    public ResponseEntity<ApiResponse<RideResponseDTO>> completeRide(
            @PathVariable UUID rideId) {

        return ResponseEntity.ok(
                ApiResponseBuilder.success(
                        rideService.completeRide(rideId),
                        "Ride completed successfully"
                )
        );
    }
    @PostMapping("/{id}/match")
    public ResponseEntity<ApiResponse<RideResponseDTO>> match(@PathVariable UUID id) {

        return ResponseEntity.ok(
                ApiResponseBuilder.success(
                        rideService.matchRide(id), "Matching started"
                )
        );
    }


}
