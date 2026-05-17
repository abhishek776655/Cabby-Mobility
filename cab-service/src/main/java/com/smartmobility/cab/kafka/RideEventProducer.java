package com.smartmobility.cab.kafka;

import com.smartmobility.cab.event.DriverResponseEvent;
import com.smartmobility.cab.event.RideRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RideEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String RIDE_REQUESTED_TOPIC = "ride-requested";
    private static final String ASSIGNMENT_ACCEPTED_TOPIC = "assignment-accepted";
    private static final String ASSIGNMENT_REJECTED_TOPIC = "assignment-rejected";

    public void publishRideRequested(RideRequestedEvent event) {
        kafkaTemplate.send(RIDE_REQUESTED_TOPIC, event.getRideId().toString(), event);
    }

    public void publishDriverResponse(DriverResponseEvent event) {
        String topic = event.isAccepted() ? ASSIGNMENT_ACCEPTED_TOPIC : ASSIGNMENT_REJECTED_TOPIC;
        String key = event.getRideId() != null
                ? event.getRideId().toString()
                : event.getDispatchId().toString();
        kafkaTemplate.send(topic, key, event);
    }
}
