package com.smartmobility.pricing.domain;

import com.smartmobility.pricing.entity.RateCardEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FareCalculatorTest {

    @Test
    void testCalculate_NormalFare() {
        // Arrange
        RateCardEntity rateCard = RateCardEntity.builder()
                .baseFare(5000) // 50.00
                .perKmRate(1500) // 15.00
                .perMinRate(200) // 2.00
                .minFare(8000) // 80.00
                .build();
        
        long distanceMeters = 5000; // 5 km
        long durationSeconds = 900; // 15 mins
        double surgeMultiplier = 1.0;

        // Act
        FareCalculator.FareBreakdown breakdown = FareCalculator.calculate(distanceMeters, durationSeconds, rateCard, surgeMultiplier);

        // Assert
        // Base: 5000
        // Dist: 5 * 1500 = 7500
        // Time: 15 * 200 = 3000
        // Subtotal: 15500
        
        assertEquals(5000, breakdown.getBaseFare());
        assertEquals(7500, breakdown.getDistanceFare());
        assertEquals(3000, breakdown.getTimeFare());
        assertEquals(0, breakdown.getSurgeAmount());
        assertEquals(15500, breakdown.getTotal());
        assertEquals(1.0, breakdown.getSurgeMultiplier());
    }

    @Test
    void testCalculate_MinimumFare() {
        // Arrange
        RateCardEntity rateCard = RateCardEntity.builder()
                .baseFare(5000) // 50.00
                .perKmRate(1500) // 15.00
                .perMinRate(200) // 2.00
                .minFare(10000) // 100.00 (High min fare)
                .build();
        
        long distanceMeters = 1000; // 1 km
        long durationSeconds = 300; // 5 mins
        double surgeMultiplier = 1.0;

        // Act
        FareCalculator.FareBreakdown breakdown = FareCalculator.calculate(distanceMeters, durationSeconds, rateCard, surgeMultiplier);

        // Assert
        // Dist: 1 * 1500 = 1500
        // Time: 5 * 200 = 1000
        // Initial Subtotal: 5000 + 1500 + 1000 = 7500
        // Minimum Fare adjustment: 7500 < 10000, so baseFare becomes 5000 + (10000 - 7500) = 7500
        // Total should be exactly 10000
        
        assertEquals(7500, breakdown.getBaseFare()); // Adjusted base fare
        assertEquals(1500, breakdown.getDistanceFare());
        assertEquals(1000, breakdown.getTimeFare());
        assertEquals(0, breakdown.getSurgeAmount());
        assertEquals(10000, breakdown.getTotal());
    }

    @Test
    void testCalculate_WithSurge() {
        // Arrange
        RateCardEntity rateCard = RateCardEntity.builder()
                .baseFare(5000)
                .perKmRate(1500)
                .perMinRate(200)
                .minFare(8000)
                .build();
        
        long distanceMeters = 5000; // 5 km
        long durationSeconds = 900; // 15 mins
        double surgeMultiplier = 1.5;

        // Act
        FareCalculator.FareBreakdown breakdown = FareCalculator.calculate(distanceMeters, durationSeconds, rateCard, surgeMultiplier);

        // Assert
        // Subtotal: 15500
        // Surge: 15500 * 1.5 = 23250
        // SurgeAmount: 23250 - 15500 = 7750
        
        assertEquals(5000, breakdown.getBaseFare());
        assertEquals(7500, breakdown.getDistanceFare());
        assertEquals(3000, breakdown.getTimeFare());
        assertEquals(7750, breakdown.getSurgeAmount());
        assertEquals(23250, breakdown.getTotal());
        assertEquals(1.5, breakdown.getSurgeMultiplier());
    }
}
