package com.smartmobility.rider_service.kafka.consumer;

import com.smartmobility.rider_service.entity.RiderEntity;
import com.smartmobility.rider_service.event.UserCreatedEvent;
import com.smartmobility.rider_service.repository.RiderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UserCreatedConsumerTest {

    private RiderRepository riderRepository;
    private UserCreatedConsumer userCreatedConsumer;

    @BeforeEach
    public void setUp() {
        riderRepository = mock(RiderRepository.class);
        userCreatedConsumer = new UserCreatedConsumer(riderRepository);
    }

    @Test
    public void whenRiderUserCreated_thenRiderProfileSaved() {
        UserCreatedEvent event = new UserCreatedEvent(105L, "jane.rider@example.com", Set.of("RIDER"));

        when(riderRepository.findByUserId(105L)).thenReturn(Optional.empty());

        userCreatedConsumer.consume(event);

        verify(riderRepository, times(1)).save(any(RiderEntity.class));
    }

    @Test
    public void whenNonRiderUserCreated_thenIgnored() {
        UserCreatedEvent event = new UserCreatedEvent(106L, "jack.driver@example.com", Set.of("DRIVER"));

        userCreatedConsumer.consume(event);

        verify(riderRepository, never()).save(any(RiderEntity.class));
    }

    @Test
    public void whenRiderUserCreatedDuplicate_thenIgnoredIdempotently() {
        UserCreatedEvent event = new UserCreatedEvent(105L, "jane.rider@example.com", Set.of("RIDER"));

        // Mock that the rider profile already exists
        RiderEntity existingRider = RiderEntity.builder().id(1L).userId(105L).build();
        when(riderRepository.findByUserId(105L)).thenReturn(Optional.of(existingRider));

        userCreatedConsumer.consume(event);

        // Verification: should call findByUserId, but never save since it's duplicate
        verify(riderRepository, times(1)).findByUserId(105L);
        verify(riderRepository, never()).save(any(RiderEntity.class));
    }
}
