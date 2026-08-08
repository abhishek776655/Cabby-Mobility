package com.smartmobility.cab.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmobility.cab.entity.OutboxEvent;
import com.smartmobility.cab.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000) // Poll every 1 second
    @Transactional
    public void relayEventsToKafka() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByProcessedFalseOrderByCreatedAtAsc();

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending events to relay", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // Parse the JSON string to JsonNode so that JsonSerializer doesn't double-quote it
                JsonNode payloadNode = objectMapper.readTree(event.getPayload());
                
                // Send to Kafka
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), payloadNode);
                
                // Mark as processed
                event.setProcessed(true);
                outboxEventRepository.save(event);
                
                log.debug("Successfully relayed outbox event ID: {}", event.getId());
            } catch (Exception e) {
                log.error("Failed to process outbox event ID: {}", event.getId(), e);
                // We don't rethrow here so that one bad event doesn't block processing of others in the batch,
                // or we could throw to let the transaction rollback. Since we catch, only this event remains unprocessed
                // (actually if we save within the loop, wait - we are inside @Transactional, if we catch and continue, 
                // the other saves might commit, but if we don't save the bad event, it remains unprocessed).
            }
        }
    }
}
