package com.smartmobility.routing_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RouteRequest {
    @NotNull
    private Double originLat;
    @NotNull
    private Double originLng;
    @NotNull
    private Double destLat;
    @NotNull
    private Double destLng;
    
    private String costingModel = "auto";
}
