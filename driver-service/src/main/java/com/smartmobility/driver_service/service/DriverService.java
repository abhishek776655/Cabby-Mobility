package com.smartmobility.driver_service.service;

import com.smartmobility.driver_service.dto.CreateDriverRequestDTO;
import com.smartmobility.driver_service.dto.DriverResponseDTO;

public interface DriverService {

    DriverResponseDTO createDriver(CreateDriverRequestDTO request);

    DriverResponseDTO getDriver(Long userId);

    DriverResponseDTO updateAvailability(Long userId, Boolean available);

}
