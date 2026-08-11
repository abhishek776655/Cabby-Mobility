package com.smartmobility.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareQuoteRequest {
    @NotNull
    private Double pickupLat;
    @NotNull
    private Double pickupLng;
    @NotNull
    private Double dropLat;
    @NotNull
    private Double dropLng;
    @NotBlank
    private String vehicleType;
    /**
     * Optional client-supplied key. When set and already seen, quote() returns the existing
     * estimate instead of inserting a duplicate row — protects against cab-service retrying
     * a timed-out request.
     */
    private String idempotencyKey;
}
