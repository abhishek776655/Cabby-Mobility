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
    private UUID riderId;
    private UUID driverId;

    private String pickupLocation;
    private String dropLocation;

    private String status;

    private Double fare;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
