package com.smartmobility.pricing.dto;

import com.smartmobility.pricing.domain.FareCalculator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareFinalizeResponse {
    private String rideId;
    private FareCalculator.FareBreakdown finalBreakdown;
    private String currency;
    private String calculationSource;
}
