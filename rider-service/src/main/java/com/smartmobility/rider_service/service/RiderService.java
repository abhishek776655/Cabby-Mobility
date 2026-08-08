package com.smartmobility.rider_service.service;

import com.smartmobility.rider_service.dto.RiderResponseDTO;
import com.smartmobility.rider_service.dto.UpdatePreferencesRequestDTO;

public interface RiderService {
    RiderResponseDTO getRiderByUserId(Long userId);
    RiderResponseDTO updatePreferences(Long userId, UpdatePreferencesRequestDTO request);
}
