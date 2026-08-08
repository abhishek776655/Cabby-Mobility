package com.smartmobility.rider_service.service;

import com.smartmobility.rider_service.dto.CreateSavedLocationRequestDTO;
import com.smartmobility.rider_service.dto.RiderSavedLocationResponseDTO;
import java.util.List;

public interface SavedLocationService {
    List<RiderSavedLocationResponseDTO> getSavedLocations(Long userId);
    RiderSavedLocationResponseDTO addSavedLocation(Long userId, CreateSavedLocationRequestDTO request);
    void deleteSavedLocation(Long userId, Long locationId);
}
