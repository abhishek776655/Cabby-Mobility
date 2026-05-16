package com.smartmobility.matchmaking.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmobility.matchmaking.event.AssignmentAcceptedEvent;
import com.smartmobility.matchmaking.service.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentAcceptedConsumer {

    private final DispatchService dispatchService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "assignment-accepted",
        groupId = "matchmaking-service-group"
    )
    public void consume(String message) {
        log.info("Received AssignmentAccepted: {}", message);
        try {
            AssignmentAcceptedEvent event = objectMapper.readValue(message, AssignmentAcceptedEvent.class);
            
            if (event.getDispatchId() != null && event.getDriverId() != null) {
                dispatchService.handleDriverResponse(event.getDispatchId(), event.getDriverId(), true);
            }
        } catch (Exception e) {
            log.error("Failed to process AssignmentAccepted: {}", message, e);
        }
    }
}