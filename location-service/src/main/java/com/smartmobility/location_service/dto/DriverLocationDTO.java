package com.smartmobility.location_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverLocationDTO {
    private Long driverUserId;
    private Double lat;
    private Double lng;
}
