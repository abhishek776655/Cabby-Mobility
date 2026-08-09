package com.smartmobility.matchmaking.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class MatrixRequest {
    private List<Location> sources;
    private List<Location> targets;
    private String costingModel;

    @Data
    @Builder
    public static class Location {
        private Double lat;
        private Double lng;
    }
}
