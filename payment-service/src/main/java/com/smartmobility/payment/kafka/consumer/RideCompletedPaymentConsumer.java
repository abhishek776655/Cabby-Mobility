package com.smartmobility.payment.kafka.consumer;

import com.smartmobility.payment.event.RideCompletedEvent;
import com.smartmobility.payment.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideCompletedPaymentConsumer {

    private final ObjectMapper objectMapper;
    private final WalletService walletService;

    @KafkaListener(topics = "ride-completed", groupId = "payment-service-group", containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message) {
        log.info("Received ride-completed event for payment processing");
        try {
            RideCompletedEvent event = objectMapper.readValue(message, RideCompletedEvent.class);

            if (event.getFare() == null || event.getFare() <= 0) {
                log.info("Ride {} has no chargeable fare, skipping payment", event.getRideId());
                return;
            }

            walletService.debit(
                    event.getRiderUserId(),
                    Math.round(event.getFare()),
                    event.getEventId(),
                    event.getRideId().toString());
        } catch (Exception e) {
            log.error("Failed to process ride-completed payment event: {}", message, e);
            throw new IllegalArgumentException("Invalid message format", e);
        }
    }
}
