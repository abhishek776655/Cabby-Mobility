package com.smartmobility.matchmaking.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmobility.matchmaking.event.AssignmentRejectedEvent;
import com.smartmobility.matchmaking.service.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentRejectedConsumer {

    private final DispatchService dispatchService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "assignment-rejected",
        groupId = "matchmaking-service-group"
    )
    public void consume(String message) {
        log.info("Received AssignmentRejected: {}", message);
        try {
            AssignmentRejectedEvent event = objectMapper.readValue(message, AssignmentRejectedEvent.class);
            
            if (event.getDispatchId() != null && event.getDriverId() != null) {
                dispatchService.handleDriverResponse(event.getDispatchId(), event.getDriverId(), false);
            }
        } catch (Exception e) {
            log.error("Failed to process AssignmentRejected: {}", message, e);
        }
    }
}