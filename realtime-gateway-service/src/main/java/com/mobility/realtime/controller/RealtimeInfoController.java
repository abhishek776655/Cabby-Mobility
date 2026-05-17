package com.mobility.realtime.controller;

import com.mobility.realtime.websocket.WebSocketTopics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/realtime")
public class RealtimeInfoController {

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "service", "realtime-gateway-service",
                "websocketEndpoint", WebSocketTopics.ENDPOINT,
                "riderSubscription", WebSocketTopics.RIDER_TRIP_SUBSCRIPTION,
                "driverSubscription", WebSocketTopics.DRIVER_ASSIGNMENT_SUBSCRIPTION,
                "stateless", true
        );
    }
}
