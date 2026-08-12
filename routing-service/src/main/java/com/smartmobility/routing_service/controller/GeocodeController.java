package com.smartmobility.routing_service.controller;

import com.smartmobility.routing_service.dto.ApiResponse;
import com.smartmobility.routing_service.dto.GeocodeSuggestion;
import com.smartmobility.routing_service.service.GeocodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sits under /internal like the rest of this service; the gateway rewrites the rider-facing
 * /geocode/** path onto it and injects the internal secret, matching how /fares and /wallet
 * already reach pricing-service and payment-service.
 */
@RestController
@RequestMapping("/internal/geocode")
@RequiredArgsConstructor
public class GeocodeController {

    private final GeocodeService geocodeService;

    @GetMapping("/autocomplete")
    public ResponseEntity<ApiResponse<List<GeocodeSuggestion>>> autocomplete(
            @RequestParam("q") String query,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lon", required = false) Double lon,
            @RequestParam(value = "limit", required = false) Integer limit) {

        List<GeocodeSuggestion> suggestions = geocodeService.autocomplete(query, lat, lon, limit);
        return ResponseEntity.ok(
                ApiResponse.<List<GeocodeSuggestion>>builder().success(true).data(suggestions).build());
    }

    /**
     * Names the place under a map pin. Returns 204 rather than an error when the point has no
     * match or sits outside the serviceable area — "nothing here" is an ordinary outcome the
     * map picker renders as a prompt, not a failure worth an error banner.
     */
    @GetMapping("/reverse")
    public ResponseEntity<ApiResponse<GeocodeSuggestion>> reverse(
            @RequestParam("lat") double lat,
            @RequestParam("lon") double lon) {

        return geocodeService.reverse(lat, lon)
                .map(suggestion -> ResponseEntity.ok(
                        ApiResponse.<GeocodeSuggestion>builder().success(true).data(suggestion).build()))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
