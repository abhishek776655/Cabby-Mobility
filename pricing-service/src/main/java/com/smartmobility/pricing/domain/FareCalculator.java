package com.smartmobility.pricing.domain;

import com.smartmobility.pricing.entity.RateCardEntity;
import lombok.Builder;
import lombok.Data;

public class FareCalculator {

    @Data
    @Builder
    public static class FareBreakdown {
        private long baseFare;
        private long distanceFare;
        private long timeFare;
        private long surgeAmount;
        private long total;
        private double surgeMultiplier;
    }

    /**
     * Calculates the fare in smallest currency unit (paise/cents).
     *
     * @param distanceMeters The exact road-network distance in meters
     * @param durationSeconds The exact road-network duration in seconds
     * @param rateCard The rate card for the vehicle type
     * @param surgeMultiplier The surge multiplier for the zone (e.g., 1.5)
     * @return FareBreakdown with exact calculations
     */
    public static FareBreakdown calculate(long distanceMeters, long durationSeconds, RateCardEntity rateCard, double surgeMultiplier) {
        if (rateCard == null) {
            throw new IllegalArgumentException("RateCard cannot be null");
        }
        
        long baseFare = rateCard.getBaseFare();
        
        double distanceKm = distanceMeters / 1000.0;
        long distanceFare = (long) (distanceKm * rateCard.getPerKmRate());
        
        double durationMinutes = durationSeconds / 60.0;
        long timeFare = (long) (durationMinutes * rateCard.getPerMinRate());
        
        long subtotal = baseFare + distanceFare + timeFare;
        
        // Ensure subtotal meets minimum fare before applying surge
        if (subtotal < rateCard.getMinFare()) {
            // Adjust base fare so that subtotal equals min fare
            baseFare += (rateCard.getMinFare() - subtotal);
            subtotal = rateCard.getMinFare();
        }
        
        // Ensure surge multiplier is at least 1.0
        double effectiveSurge = Math.max(1.0, surgeMultiplier);
        
        long totalWithSurge = (long) (subtotal * effectiveSurge);
        long surgeAmount = totalWithSurge - subtotal;
        
        return FareBreakdown.builder()
                .baseFare(baseFare)
                .distanceFare(distanceFare)
                .timeFare(timeFare)
                .surgeAmount(surgeAmount)
                .total(totalWithSurge)
                .surgeMultiplier(effectiveSurge)
                .build();
    }
}
