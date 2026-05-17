package com.mobility.realtime.service;

import com.mobility.realtime.domain.RealtimeDestinations;
import com.mobility.realtime.dto.AssignmentRequestedEvent;
import com.mobility.realtime.dto.DriverLocationUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastDriverLocation(DriverLocationUpdatedEvent event) {
        String destination = RealtimeDestinations.trip(event.getRideId());
        messagingTemplate.convertAndSend(destination, event);
        log.debug("Broadcasted driver location rideId={} driverUserId={} destination={}",
                event.getRideId(), event.getDriverUserId(), destination);
    }

    public void broadcastAssignmentRequest(AssignmentRequestedEvent event) {
        String destination = RealtimeDestinations.driver(event.getDriverUserId());
        messagingTemplate.convertAndSend(destination, event);
        log.info("Broadcasted assignment request driverUserId={} rideId={} dispatchId={} destination={}",
                event.getDriverUserId(), event.getRideId(), event.getDispatchId(), destination);
    }
}
