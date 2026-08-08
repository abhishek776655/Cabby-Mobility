package com.smartmobility.rider_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiderResponseDTO {
    private Long id;
    private Long userId;
    private Double rating;
    private String preferredPaymentMethod;
}
