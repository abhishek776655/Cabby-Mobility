package com.smartmobility.matchmaking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NearbyDriversRequest {
    private Double lat;
    private Double lng;
    private Double radiusKm;
    private Integer limit;
}