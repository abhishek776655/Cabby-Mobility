package com.smartmobility.rider_service.repository;

import com.smartmobility.rider_service.entity.RiderEntity;
import com.smartmobility.rider_service.entity.RiderSavedLocationEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
public class RiderSavedLocationRepositoryTest {

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private RiderSavedLocationRepository locationRepository;

    private RiderEntity rider;

    @BeforeEach
    public void setUp() {
        rider = RiderEntity.builder()
                .userId(10L)
                .rating(5.0)
                .preferredPaymentMethod("CASH")
                .build();
        rider = riderRepository.saveAndFlush(rider);
    }

    @Test
    public void whenSaveLocation_thenCorrectlyPersisted() {
        RiderSavedLocationEntity location = RiderSavedLocationEntity.builder()
                .rider(rider)
                .label("Home")
                .address("123 Home St")
                .latitude(37.7749)
                .longitude(-122.4194)
                .build();

        RiderSavedLocationEntity savedLocation = locationRepository.saveAndFlush(location);

        assertNotNull(savedLocation.getId());
        assertEquals("HOME", savedLocation.getLabel());
        assertEquals("123 Home St", savedLocation.getAddress());
        assertEquals(37.7749, savedLocation.getLatitude());
        assertEquals(-122.4194, savedLocation.getLongitude());
        assertNotNull(savedLocation.getCreatedAt());
    }

    @Test
    public void whenSaveDuplicateLabelsSameRiderCaseInsensitive_thenThrowsException() {
        RiderSavedLocationEntity loc1 = RiderSavedLocationEntity.builder()
                .rider(rider)
                .label("Home")
                .address("123 First St")
                .latitude(37.7749)
                .longitude(-122.4194)
                .build();

        RiderSavedLocationEntity loc2 = RiderSavedLocationEntity.builder()
                .rider(rider)
                .label("home") // Case-insensitive duplicate label
                .address("456 Second St")
                .latitude(37.7749)
                .longitude(-122.4194)
                .build();

        locationRepository.saveAndFlush(loc1);

        // This assertion will fail right now because there is no composite unique index in RiderSavedLocationEntity!
        assertThrows(DataIntegrityViolationException.class, () -> {
            locationRepository.saveAndFlush(loc2);
        });
    }

    @Test
    public void whenSaveSameLabelDifferentRiders_thenSucceeds() {
        RiderEntity otherRider = RiderEntity.builder()
                .userId(20L)
                .rating(5.0)
                .preferredPaymentMethod("CASH")
                .build();
        otherRider = riderRepository.saveAndFlush(otherRider);

        RiderSavedLocationEntity loc1 = RiderSavedLocationEntity.builder()
                .rider(rider)
                .label("Home")
                .address("123 First St")
                .latitude(37.7749)
                .longitude(-122.4194)
                .build();

        RiderSavedLocationEntity loc2 = RiderSavedLocationEntity.builder()
                .rider(otherRider)
                .label("Home")
                .address("456 Second St")
                .latitude(37.7749)
                .longitude(-122.4194)
                .build();

        assertDoesNotThrow(() -> {
            locationRepository.saveAndFlush(loc1);
            locationRepository.saveAndFlush(loc2);
        });
    }
}
