package com.smartmobility.matchmaking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverResponseDTO {
    private Long userId;
    private Double rating;
    private Boolean available;
    private String vehicleDetails;
}