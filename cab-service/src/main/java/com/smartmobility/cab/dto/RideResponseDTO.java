package com.smartmobility.cab.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideResponseDTO {

    private UUID rideId;
    private Long riderUserId;
    private Long driverUserId;

    private String pickupLocation;
    private String dropLocation;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double dropLatitude;
    private Double dropLongitude;

    private String status;

    private Double fare;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
