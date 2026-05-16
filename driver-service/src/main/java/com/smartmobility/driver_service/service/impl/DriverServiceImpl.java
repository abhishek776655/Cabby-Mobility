package com.smartmobility.driver_service.service.impl;

import com.smartmobility.driver_service.dto.CreateDriverRequestDTO;
import com.smartmobility.driver_service.dto.DriverResponseDTO;
import com.smartmobility.driver_service.entity.DriverEntity;
import com.smartmobility.driver_service.exception.DriverAlreadyExistsException;
import com.smartmobility.driver_service.exception.DriverNotFoundException;
import com.smartmobility.driver_service.repository.DriverRepository;
import com.smartmobility.driver_service.service.DriverService;
import com.smartmobility.driver_service.mapper.DriverMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DriverServiceImpl implements DriverService {

    private final DriverRepository driverRepository;

    @Override
    public DriverResponseDTO createDriver(CreateDriverRequestDTO request) {

        // 🟡 Layer 1: fast idempotency check
        if (driverRepository.existsByUserId(request.getUserId())) {
            throw new DriverAlreadyExistsException("Driver already exists");
        }

        try {
            DriverEntity entity = DriverMapper.toEntity(request);

            DriverEntity saved = driverRepository.save(entity);

            return DriverMapper.toDTO(saved);

        } catch (DataIntegrityViolationException ex) {
            throw new DriverAlreadyExistsException("Driver already exists (duplicate event)");
        }
    }

    @Override
    public DriverResponseDTO getDriver(Long userId) {

        DriverEntity entity = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new DriverNotFoundException("Driver not found"));

        return DriverMapper.toDTO(entity);
    }

    @Override
    public DriverResponseDTO updateAvailability(Long userId, Boolean available) {

        DriverEntity entity = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new DriverNotFoundException("Driver not found"));

        entity.setAvailable(available);

        DriverEntity updated = driverRepository.save(entity);

        return DriverMapper.toDTO(updated);
    }
}