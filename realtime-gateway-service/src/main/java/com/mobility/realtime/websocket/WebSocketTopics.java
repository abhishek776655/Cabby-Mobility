package com.mobility.realtime.websocket;

public final class WebSocketTopics {

    public static final String ENDPOINT = "/ws";
    public static final String APPLICATION_PREFIX = "/app";
    public static final String TOPIC_PREFIX = "/topic";
    public static final String RIDER_TRIP_SUBSCRIPTION = "/topic/trip/{rideId}";
    public static final String DRIVER_ASSIGNMENT_SUBSCRIPTION = "/topic/driver/{driverUserId}";

    private WebSocketTopics() {
    }
}
