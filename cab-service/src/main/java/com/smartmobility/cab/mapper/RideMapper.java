package com.smartmobility.cab.mapper;


import com.smartmobility.cab.dto.RideRequestDTO;
import com.smartmobility.cab.dto.RideResponseDTO;
import com.smartmobility.cab.entity.RideEntity;
import com.smartmobility.cab.entity.RideStatus;

public class RideMapper {

    public static RideEntity toEntity(RideRequestDTO request) {
        return RideEntity.builder()
                .riderId(request.getRiderId())
                .pickupLocation(request.getPickupLocation())
                .dropLocation(request.getDropLocation())
                .status(RideStatus.REQUESTED) // always initial state
                .build();
    }

    public static RideResponseDTO toResponseDTO(RideEntity ride) {
        return RideResponseDTO.builder()
                .rideId(ride.getId())
                .riderId(ride.getRiderId())
                .driverId(ride.getDriverId())
                .pickupLocation(ride.getPickupLocation())
                .dropLocation(ride.getDropLocation())
                .status(ride.getStatus().name())
                .fare(ride.getFare())
                .createdAt(ride.getCreatedAt())
                .updatedAt(ride.getUpdatedAt())
                .build();
    }
}