package com.smartmobility.location_service.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class NearbyDriversRequest {

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private double lat;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private double lng;

    @Positive
    @DecimalMax(value = "50.0", message = "radiusKm must not exceed 50km")
    private double radiusKm;

    @Positive
    @Max(value = 200, message = "limit must not exceed 200")
    private int limit;
}