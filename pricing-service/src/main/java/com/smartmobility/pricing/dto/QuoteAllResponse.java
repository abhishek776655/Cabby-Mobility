package com.smartmobility.pricing.dto;

import com.smartmobility.pricing.domain.FareCalculator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteAllResponse {
    // Route is fetched once and shared across every vehicle type below —
    // distance/time never differ by vehicle, only the fare math does.
    private String polyline;
    private List<Coordinate> coordinates;
    private double distanceMeters;
    private double durationSeconds;
    private String estimateSource; // "VALHALLA" or "FALLBACK"
    private String currency;
    private List<VehicleQuote> quotes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VehicleQuote {
        private String vehicleType;
        private FareCalculator.FareBreakdown breakdown;
    }
}
