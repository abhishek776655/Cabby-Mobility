package com.smartmobility.matchmaking.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmobility.matchmaking.entity.OutboxEvent;
import com.smartmobility.matchmaking.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        OutboxEvent event = OutboxEvent.builder()
            .id(1L).aggregateId("ride-123").eventType("driver-assigned")
            .topic("driver-assigned").payload("{\"rideId\":\"ride-123\"}").processed(false)
            .build();
        when(outboxEventRepository.findByProcessedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));

        scheduler.relayEventsToKafka();

        verify(kafkaTemplate).send(eq("driver-assigned"), eq("ride-123"), any());
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
