package com.smartmobility.location_service.dto;


import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UpdateLocationRequest {

    @NotBlank
    private String driverId;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private double lat;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private double lng;
}