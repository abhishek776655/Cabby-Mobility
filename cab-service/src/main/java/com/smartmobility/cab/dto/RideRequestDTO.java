package com.smartmobility.cab.dto;


import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideRequestDTO {

    @NotNull
    private UUID riderId;

    @NotNull
    private String pickupLocation;

    @NotNull
    private String dropLocation;
}