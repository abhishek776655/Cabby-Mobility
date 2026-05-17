package com.mobility.realtime.kafka;

import com.mobility.realtime.dto.AssignmentRequestedEvent;
import com.mobility.realtime.exception.InvalidRealtimeEventException;
import com.mobility.realtime.service.RealtimeBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverAssignmentConsumer {

    private final RealtimeBroadcastService broadcastService;

    @KafkaListener(
            topics = "${realtime.kafka.topics.assignment-requested-events}",
            containerFactory = "assignmentRequestedKafkaListenerContainerFactory"
    )
    public void consume(AssignmentRequestedEvent event) {
        if (event == null || event.getDriverUserId() == null) {
            throw new InvalidRealtimeEventException("Assignment requested event must include driverUserId");
        }
        log.info("Consumed assignment requested event driverUserId={} rideId={} dispatchId={}",
                event.getDriverUserId(), event.getRideId(), event.getDispatchId());
        broadcastService.broadcastAssignmentRequest(event);
    }
}
