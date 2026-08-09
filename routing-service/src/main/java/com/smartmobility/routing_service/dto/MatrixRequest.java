package com.smartmobility.routing_service.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class MatrixRequest {
    @NotEmpty
    private List<Location> sources;
    @NotEmpty
    private List<Location> targets;
    
    private String costingModel = "auto";

    @Data
    public static class Location {
        private Double lat;
        private Double lng;
    }
}
