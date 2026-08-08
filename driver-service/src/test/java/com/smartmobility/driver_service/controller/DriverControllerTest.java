package com.smartmobility.driver_service.controller;

import com.smartmobility.driver_service.dto.DriverResponseDTO;
import com.smartmobility.driver_service.dto.ApiResponse;
import com.smartmobility.driver_service.security.DriverAuthorizationGuard;
import com.smartmobility.driver_service.service.DriverService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverControllerTest {

    @Test
    void getDriverInternalShouldReturnDriverWithoutHeaders() {
        DriverResponseDTO expected = DriverResponseDTO.builder()
                .userId(1024L)
                .available(true)
                .rating(4.9)
                .vehicleDetails("Sedan")
                .build();

        DriverService driverService = new DriverService() {
            @Override
            public DriverResponseDTO createDriver(com.smartmobility.driver_service.dto.CreateDriverRequestDTO request) {
                throw new UnsupportedOperationException("Not needed");
            }

            @Override
            public DriverResponseDTO getDriver(Long userId) {
                assertEquals(1024L, userId);
                return expected;
            }

            @Override
            public List<DriverResponseDTO> getDriversBatch(List<Long> userIds) {
                throw new UnsupportedOperationException("Not needed");
            }

            @Override
            public DriverResponseDTO updateAvailability(Long userId, Boolean available) {
                throw new UnsupportedOperationException("Not needed");
            }
        };

        DriverController controller = new DriverController(driverService, new DriverAuthorizationGuard());

        ResponseEntity<ApiResponse<DriverResponseDTO>> responseEntity = controller.getDriverInternal(1024L);

        assertNotNull(responseEntity);
        assertTrue(responseEntity.getStatusCode().is2xxSuccessful());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals("Found Driver", responseEntity.getBody().getMessage());
        assertEquals(expected, responseEntity.getBody().getData());
    }
}
