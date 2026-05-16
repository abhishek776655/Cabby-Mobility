package com.smartmobility.driver_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateDriverRequestDTO {

    @NotNull
    private Long userId;

    private String vehicleDetails;
}
