package com.smartmobility.matchmaking.kafka;

import com.smartmobility.matchmaking.entity.OutboxEvent;
import com.smartmobility.matchmaking.event.DriverAssignedEvent;
import com.smartmobility.matchmaking.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchmakingEventProducerImplTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private MatchmakingEventProducerImpl producer;

    @BeforeEach
    void setUp() {
        producer = new MatchmakingEventProducerImpl(outboxEventRepository, new tools.jackson.databind.ObjectMapper());
    }

    @Test
    void publishDriverAssignedWritesToOutboxInsteadOfKafkaDirectly() {
        UUID rideId = UUID.randomUUID();
        DriverAssignedEvent event = DriverAssignedEvent.builder()
            .eventId("evt-1").rideId(rideId).driverUserId(42L)
            .riderUserId(7L).assignedAt(LocalDateTime.now()).build();

        producer.publishDriverAssigned(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertEquals("driver-assigned", saved.getTopic());
        assertEquals(rideId.toString(), saved.getAggregateId());
        assertFalse(saved.isProcessed());
    }
}
