package com.smartmobility.pricing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteAllRequest {
    @NotNull
    private Double pickupLat;
    @NotNull
    private Double pickupLng;
    @NotNull
    private Double dropLat;
    @NotNull
    private Double dropLng;
}
