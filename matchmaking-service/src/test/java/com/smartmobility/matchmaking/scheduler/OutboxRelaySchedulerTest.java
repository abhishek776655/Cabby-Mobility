package com.smartmobility.matchmaking.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmobility.matchmaking.entity.OutboxEvent;
import com.smartmobility.matchmaking.event.DriverAssignedEvent;
import com.smartmobility.matchmaking.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private OutboxRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutboxRelayScheduler(outboxEventRepository, kafkaTemplate, new ObjectMapper());
    }

    @Test
    void relaysPendingEventsAndMarksThemProcessed() {
        String rideId = "8dc18620-00e2-4872-ae28-bebf6deb0749";
        String payload = "{\"eventId\":\"evt-1\",\"rideId\":\"" + rideId + "\",\"driverUserId\":42}";
        OutboxEvent event = OutboxEvent.builder()
            .id(1L).aggregateId(rideId).eventType("driver-assigned")
            .topic("driver-assigned").payload(payload).processed(false)
            .build();
        when(outboxEventRepository.findByProcessedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(kafkaTemplate.send(eq("driver-assigned"), eq(rideId), any(DriverAssignedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        scheduler.relayEventsToKafka();

        ArgumentCaptor<DriverAssignedEvent> payloadCaptor = ArgumentCaptor.forClass(DriverAssignedEvent.class);
        verify(kafkaTemplate).send(eq("driver-assigned"), eq(rideId), payloadCaptor.capture());
        DriverAssignedEvent sent = payloadCaptor.getValue();
        assert sent.getRideId().toString().equals(rideId);
        assert sent.getDriverUserId().equals(42L);

        ArgumentCaptor<OutboxEvent> savedCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(savedCaptor.capture());
        assert savedCaptor.getValue().isProcessed();
    }

    @Test
    void noPendingEventsDoesNothing() {
        when(outboxEventRepository.findByProcessedFalseOrderByCreatedAtAsc()).thenReturn(List.of());

        scheduler.relayEventsToKafka();

        verifyNoInteractions(kafkaTemplate);
    }
}
