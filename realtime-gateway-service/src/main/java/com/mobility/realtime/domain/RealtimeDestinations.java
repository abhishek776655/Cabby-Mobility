package com.mobility.realtime.domain;

import com.mobility.realtime.util.StringSanitizer;

public final class RealtimeDestinations {

    public static final String TOPIC_PREFIX = "/topic";
    public static final String TRIP_TOPIC_PREFIX = TOPIC_PREFIX + "/trip";
    public static final String DRIVER_TOPIC_PREFIX = TOPIC_PREFIX + "/driver";

    private RealtimeDestinations() {
    }

    public static String trip(String rideId) {
        return TRIP_TOPIC_PREFIX + "/" + StringSanitizer.requireText(rideId, "rideId");
    }

    public static String driver(Long driverUserId) {
        if (driverUserId == null) {
            throw new IllegalArgumentException("driverUserId must not be null");
        }
        return DRIVER_TOPIC_PREFIX + "/" + driverUserId;
    }
}
