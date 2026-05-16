package com.smartmobility.driver_service.mapper;

import com.smartmobility.driver_service.dto.CreateDriverRequestDTO;
import com.smartmobility.driver_service.dto.DriverResponseDTO;
import com.smartmobility.driver_service.entity.DriverEntity;

public class DriverMapper {

    public static DriverResponseDTO toDTO(DriverEntity entity) {
        if (entity == null) return null;

        return DriverResponseDTO.builder()
                .userId(entity.getUserId())
                .rating(entity.getRating())
                .available(entity.getAvailable())
                .vehicleDetails(entity.getVehicleDetails())
                .build();
    }

    public static DriverEntity toEntity(CreateDriverRequestDTO request) {
        if (request == null) return null;

        return DriverEntity.builder()
                .userId(request.getUserId())
                .vehicleDetails(request.getVehicleDetails())
                // rating + available handled by defaults
                .build();
    }
}