package com.smartmobility.rider_service.kafka.consumer;

import com.smartmobility.rider_service.entity.RiderEntity;
import com.smartmobility.rider_service.event.UserCreatedEvent;
import com.smartmobility.rider_service.repository.RiderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedConsumer {

    private final RiderRepository riderRepository;

    @KafkaListener(
            topics = "user.created",
            groupId = "rider-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(UserCreatedEvent event) {
        log.info("Received user.created event: {}", event);

        if (event.getRoles() == null || !event.getRoles().contains("RIDER")) {
            log.info("User {} does not have RIDER role. Ignoring onboarding.", event.getUserId());
            return;
        }

        // Idempotency check: verify if rider profile already exists
        if (riderRepository.findByUserId(event.getUserId()).isPresent()) {
            log.warn("Rider profile for user {} already exists. Skipping onboarding.", event.getUserId());
            return;
        }

        try {
            RiderEntity rider = RiderEntity.builder()
                    .userId(event.getUserId())
                    .rating(5.0)
                    .preferredPaymentMethod("CASH")
                    .build();

            riderRepository.save(rider);
            log.info("Successfully onboarded rider profile for user: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to onboard rider profile for user: {}. Error: {}", event.getUserId(), e.getMessage());
        }
    }
}
