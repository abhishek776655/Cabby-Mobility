package com.smartmobility.rider_service.service.impl;

import com.smartmobility.rider_service.dto.CreateSavedLocationRequestDTO;
import com.smartmobility.rider_service.dto.RiderSavedLocationResponseDTO;
import com.smartmobility.rider_service.entity.RiderEntity;
import com.smartmobility.rider_service.entity.RiderSavedLocationEntity;
import com.smartmobility.rider_service.exception.DuplicateLocationLabelException;
import com.smartmobility.rider_service.exception.RiderNotFoundException;
import com.smartmobility.rider_service.repository.RiderRepository;
import com.smartmobility.rider_service.repository.RiderSavedLocationRepository;
import com.smartmobility.rider_service.service.SavedLocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavedLocationServiceImpl implements SavedLocationService {

    private final RiderRepository riderRepository;
    private final RiderSavedLocationRepository locationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RiderSavedLocationResponseDTO> getSavedLocations(Long userId) {
        log.info("Fetching saved locations for user ID: {}", userId);
        RiderEntity rider = riderRepository.findByUserId(userId)
                .orElseThrow(() -> new RiderNotFoundException("Rider profile not found for user ID: " + userId));

        List<RiderSavedLocationEntity> locations = locationRepository.findByRiderId(rider.getId());
        return locations.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RiderSavedLocationResponseDTO addSavedLocation(Long userId, CreateSavedLocationRequestDTO request) {
        log.info("Adding saved location for user ID: {} with label: {}", userId, request.getLabel());
        RiderEntity rider = riderRepository.findByUserId(userId)
                .orElseThrow(() -> new RiderNotFoundException("Rider profile not found for user ID: " + userId));

        // Check if label already exists (case-insensitive) for this rider
        String normalizedLabel = request.getLabel().trim().toUpperCase();
        List<RiderSavedLocationEntity> existingLocations = locationRepository.findByRiderId(rider.getId());
        boolean duplicateExists = existingLocations.stream()
                .anyMatch(loc -> loc.getLabel().toUpperCase().equals(normalizedLabel));

        if (duplicateExists) {
            throw new DuplicateLocationLabelException("Saved location with label '" + request.getLabel() + "' already exists for this rider");
        }

        RiderSavedLocationEntity location = RiderSavedLocationEntity.builder()
                .rider(rider)
                .label(request.getLabel())
                .address(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();

        RiderSavedLocationEntity savedLocation = locationRepository.save(location);
        return mapToDTO(savedLocation);
    }

    @Override
    @Transactional
    public void deleteSavedLocation(Long userId, Long locationId) {
        log.info("Deleting saved location ID: {} for user ID: {}", locationId, userId);
        RiderEntity rider = riderRepository.findByUserId(userId)
                .orElseThrow(() -> new RiderNotFoundException("Rider profile not found for user ID: " + userId));

        RiderSavedLocationEntity location = locationRepository.findById(locationId)
                .orElseThrow(() -> new RiderNotFoundException("Saved location not found with ID: " + locationId));

        if (!location.getRider().getId().equals(rider.getId())) {
            throw new IllegalArgumentException("Saved location does not belong to this user");
        }

        locationRepository.delete(location);
    }

    private RiderSavedLocationResponseDTO mapToDTO(RiderSavedLocationEntity location) {
        return RiderSavedLocationResponseDTO.builder()
                .id(location.getId())
                .riderId(location.getRider().getId())
                .label(location.getLabel())
                .address(location.getAddress())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .build();
    }
}
