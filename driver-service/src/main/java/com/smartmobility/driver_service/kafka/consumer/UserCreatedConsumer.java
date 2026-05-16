package com.smartmobility.driver_service.kafka.consumer;

import com.smartmobility.driver_service.dto.CreateDriverRequestDTO;
import com.smartmobility.driver_service.event.UserCreatedEvent;
import com.smartmobility.driver_service.service.DriverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedConsumer {

    private final DriverService driverService;

    @KafkaListener(topics = "user.created",
            groupId = "driver-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(UserCreatedEvent event) {

        log.info("Received user.created event: {}", event);

        if (event.getRoles() == null || !event.getRoles().contains("DRIVER")) {
            return;
        }

        driverService.createDriver(
                new CreateDriverRequestDTO() {{
                    setUserId(event.getUserId());
                }}
        );
    }
}