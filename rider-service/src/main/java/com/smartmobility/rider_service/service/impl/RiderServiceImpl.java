package com.smartmobility.rider_service.service.impl;

import com.smartmobility.rider_service.dto.RiderResponseDTO;
import com.smartmobility.rider_service.dto.UpdatePreferencesRequestDTO;
import com.smartmobility.rider_service.entity.RiderEntity;
import com.smartmobility.rider_service.exception.RiderNotFoundException;
import com.smartmobility.rider_service.repository.RiderRepository;
import com.smartmobility.rider_service.service.RiderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiderServiceImpl implements RiderService {

    private final RiderRepository riderRepository;

    @Override
    @Transactional(readOnly = true)
    public RiderResponseDTO getRiderByUserId(Long userId) {
        log.info("Fetching rider profile for user ID: {}", userId);
        RiderEntity rider = riderRepository.findByUserId(userId)
                .orElseThrow(() -> new RiderNotFoundException("Rider profile not found for user ID: " + userId));
        return mapToDTO(rider);
    }

    @Override
    @Transactional
    public RiderResponseDTO updatePreferences(Long userId, UpdatePreferencesRequestDTO request) {
        log.info("Updating preferred payment method for user ID: {} to {}", userId, request.getPreferredPaymentMethod());
        RiderEntity rider = riderRepository.findByUserId(userId)
                .orElseThrow(() -> new RiderNotFoundException("Rider profile not found for user ID: " + userId));

        rider.setPreferredPaymentMethod(request.getPreferredPaymentMethod());
        RiderEntity updatedRider = riderRepository.save(rider);
        return mapToDTO(updatedRider);
    }

    private RiderResponseDTO mapToDTO(RiderEntity rider) {
        return RiderResponseDTO.builder()
                .id(rider.getId())
                .userId(rider.getUserId())
                .rating(rider.getRating())
                .preferredPaymentMethod(rider.getPreferredPaymentMethod())
                .build();
    }
}
