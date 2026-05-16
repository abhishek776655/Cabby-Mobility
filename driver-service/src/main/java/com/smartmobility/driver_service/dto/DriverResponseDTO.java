package com.smartmobility.driver_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DriverResponseDTO{

    private Long userId;
    private Double rating;
    private Boolean available;
    private String vehicleDetails;
}