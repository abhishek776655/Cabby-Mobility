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
                .pickupLatitude(request.getPickupLatitude())
                .pickupLongitude(request.getPickupLongitude())
                .dropLatitude(request.getDropLatitude())
                .dropLongitude(request.getDropLongitude())
                .status(RideStatus.MATCHING)
                .build();
    }

    public static RideResponseDTO toResponseDTO(RideEntity ride) {
        return RideResponseDTO.builder()
                .rideId(ride.getId())
                .riderId(ride.getRiderId())
                .driverId(ride.getDriverId())
                .pickupLocation(ride.getPickupLocation())
                .dropLocation(ride.getDropLocation())
                .pickupLatitude(ride.getPickupLatitude())
                .pickupLongitude(ride.getPickupLongitude())
                .dropLatitude(ride.getDropLatitude())
                .dropLongitude(ride.getDropLongitude())
                .status(ride.getStatus().name())
                .fare(ride.getFare())
                .createdAt(ride.getCreatedAt())
                .updatedAt(ride.getUpdatedAt())
                .build();
    }
}
