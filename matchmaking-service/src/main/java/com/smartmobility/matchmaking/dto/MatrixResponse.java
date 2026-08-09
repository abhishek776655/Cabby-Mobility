package com.smartmobility.matchmaking.dto;

import lombok.Data;
import java.util.List;

@Data
public class MatrixResponse {
    private List<List<Double>> distancesMeters;
    private List<List<Double>> durationsSeconds;
}
