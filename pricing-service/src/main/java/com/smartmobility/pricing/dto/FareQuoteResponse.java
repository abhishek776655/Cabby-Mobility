package com.smartmobility.pricing.dto;

import com.smartmobility.pricing.domain.FareCalculator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareQuoteResponse {
    private UUID estimateId;
    private FareCalculator.FareBreakdown breakdown;
    private String currency;
    private String estimateSource; // e.g. "VALHALLA" or "FALLBACK"
    private String polyline;
    private List<Coordinate> coordinates;
    private double distanceMeters;
    private double durationSeconds;
}
