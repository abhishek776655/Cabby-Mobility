package com.smartmobility.cab.consumer;


import com.smartmobility.cab.entity.ProcessedEvent;
import com.smartmobility.cab.event.DriverAssignedEvent;
import com.smartmobility.cab.repository.ProcessedEventRepository;
import com.smartmobility.cab.service.RideService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DriverAssignedConsumer {

    private final RideService rideService;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = "driver-assigned",
            groupId = "cab-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(DriverAssignedEvent event) {
        rideService.handleDriverAssignedEvent(
                event.getEventId(),
                event.getRideId(),
                event.getDriverId()
        );
    }
}