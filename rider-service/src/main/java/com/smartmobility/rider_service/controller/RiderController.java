package com.smartmobility.rider_service.controller;

import com.smartmobility.rider_service.dto.RiderResponseDTO;
import com.smartmobility.rider_service.dto.UpdatePreferencesRequestDTO;
import com.smartmobility.rider_service.service.RiderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.smartmobility.rider_service.dto.CreateSavedLocationRequestDTO;
import com.smartmobility.rider_service.dto.RiderSavedLocationResponseDTO;
import com.smartmobility.rider_service.service.SavedLocationService;
import org.springframework.http.HttpStatus;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/riders")
@RequiredArgsConstructor
public class RiderController {

    private final RiderService riderService;
    private final SavedLocationService savedLocationService;

    @GetMapping("/me")
    public ResponseEntity<RiderResponseDTO> getMe(@RequestHeader("X-User-Id") Long userId) {
        log.info("GET /riders/me called for user ID: {}", userId);
        RiderResponseDTO response = riderService.getRiderByUserId(userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me/preferences")
    public ResponseEntity<RiderResponseDTO> updatePreferences(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody UpdatePreferencesRequestDTO request
    ) {
        log.info("PATCH /riders/me/preferences called for user ID: {} with request: {}", userId, request);
        RiderResponseDTO response = riderService.updatePreferences(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/locations")
    public ResponseEntity<List<RiderSavedLocationResponseDTO>> getLocations(@RequestHeader("X-User-Id") Long userId) {
        log.info("GET /riders/me/locations called for user ID: {}", userId);
        List<RiderSavedLocationResponseDTO> response = savedLocationService.getSavedLocations(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/me/locations")
    public ResponseEntity<RiderSavedLocationResponseDTO> addLocation(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateSavedLocationRequestDTO request
    ) {
        log.info("POST /riders/me/locations called for user ID: {} with request: {}", userId, request);
        RiderSavedLocationResponseDTO response = savedLocationService.addSavedLocation(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/me/locations/{locationId}")
    public ResponseEntity<Void> deleteLocation(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long locationId
    ) {
        log.info("DELETE /riders/me/locations/{} called for user ID: {}", locationId, userId);
        savedLocationService.deleteSavedLocation(userId, locationId);
        return ResponseEntity.noContent().build();
    }
}
