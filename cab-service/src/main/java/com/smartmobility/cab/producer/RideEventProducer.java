package com.smartmobility.cab.producer;

import com.smartmobility.cab.event.RideRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RideEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "ride-requested";

    public void publishRideRequested(RideRequestedEvent event) {
        kafkaTemplate.send(TOPIC, event.getRideId().toString(), event);
    }
}
