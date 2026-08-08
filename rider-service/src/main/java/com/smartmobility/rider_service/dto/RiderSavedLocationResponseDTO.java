package com.smartmobility.rider_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiderSavedLocationResponseDTO {
    private Long id;
    private Long riderId;
    private String label;
    private String address;
    private Double latitude;
    private Double longitude;
}
