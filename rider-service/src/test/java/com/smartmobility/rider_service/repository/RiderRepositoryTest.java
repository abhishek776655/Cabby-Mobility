package com.smartmobility.rider_service.repository;

import com.smartmobility.rider_service.entity.RiderEntity;
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
public class RiderRepositoryTest {

    @Autowired
    private RiderRepository riderRepository;

    @Test
    public void whenSaveRider_thenCorrectlyPersistedWithAuditTimestamps() {
        RiderEntity rider = RiderEntity.builder()
                .userId(1L)
                .rating(5.0)
                .preferredPaymentMethod("CARD")
                .build();

        RiderEntity savedRider = riderRepository.saveAndFlush(rider);

        assertNotNull(savedRider.getId());
        assertEquals(1L, savedRider.getUserId());
        assertEquals(5.0, savedRider.getRating());
        assertEquals("CARD", savedRider.getPreferredPaymentMethod());
        assertNotNull(savedRider.getCreatedAt());
        assertNotNull(savedRider.getUpdatedAt());
    }

    @Test
    public void whenSaveDuplicateUserId_thenThrowsException() {
        RiderEntity rider1 = RiderEntity.builder()
                .userId(100L)
                .rating(4.5)
                .preferredPaymentMethod("CASH")
                .build();

        RiderEntity rider2 = RiderEntity.builder()
                .userId(100L)
                .rating(5.0)
                .preferredPaymentMethod("CARD")
                .build();

        riderRepository.saveAndFlush(rider1);

        // This should throw DataIntegrityViolationException because of the unique user_id index constraint.
        // It will fail right now because RiderEntity doesn't have the unique index constraint yet!
        assertThrows(DataIntegrityViolationException.class, () -> {
            riderRepository.saveAndFlush(rider2);
        });
    }
}
