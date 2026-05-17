package com.smartmobility.cab.dto;


import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideRequestDTO {

    @NotNull
    private Long riderUserId;

    @NotNull
    private String pickupLocation;

    @NotNull
    private String dropLocation;

    @NotNull
    private Double pickupLatitude;

    @NotNull
    private Double pickupLongitude;

    @NotNull
    private Double dropLatitude;

    @NotNull
    private Double dropLongitude;
}
