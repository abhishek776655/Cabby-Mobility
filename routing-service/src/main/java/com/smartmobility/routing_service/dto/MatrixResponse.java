package com.smartmobility.routing_service.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class MatrixResponse {
    private List<List<Double>> distancesMeters;
    private List<List<Double>> durationsSeconds;
}
