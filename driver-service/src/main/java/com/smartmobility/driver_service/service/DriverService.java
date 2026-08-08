package com.smartmobility.driver_service.service;

import com.smartmobility.driver_service.dto.CreateDriverRequestDTO;
import com.smartmobility.driver_service.dto.DriverResponseDTO;
import java.util.List;

public interface DriverService {

    DriverResponseDTO createDriver(CreateDriverRequestDTO request);

    DriverResponseDTO getDriver(Long userId);

    List<DriverResponseDTO> getDriversBatch(List<Long> userIds);


    DriverResponseDTO updateAvailability(Long userId, Boolean available);

}
