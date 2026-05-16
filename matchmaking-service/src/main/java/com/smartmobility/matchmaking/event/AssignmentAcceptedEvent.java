package com.smartmobility.matchmaking.event;

import java.time.Instant;
import java.util.UUID;

public class AssignmentAcceptedEvent {
    private String eventId;
    private String eventType = "ASSIGNMENT_ACCEPTED";
    private UUID dispatchId;
    private UUID rideId;
    private Long driverId;
    private Instant acceptedAt;

    public static AssignmentAcceptedEventBuilder builder() {
        return new AssignmentAcceptedEventBuilder();
    }

    public static class AssignmentAcceptedEventBuilder {
        private AssignmentAcceptedEvent event = new AssignmentAcceptedEvent();

        public AssignmentAcceptedEventBuilder eventId(String eventId) { event.eventId = eventId; return this; }
        public AssignmentAcceptedEventBuilder dispatchId(UUID dispatchId) { event.dispatchId = dispatchId; return this; }
        public AssignmentAcceptedEventBuilder rideId(UUID rideId) { event.rideId = rideId; return this; }
        public AssignmentAcceptedEventBuilder driverId(Long driverId) { event.driverId = driverId; return this; }
        public AssignmentAcceptedEventBuilder acceptedAt(Instant acceptedAt) { event.acceptedAt = acceptedAt; return this; }

        public AssignmentAcceptedEvent build() {
            event.eventType = "ASSIGNMENT_ACCEPTED";
            return event;
        }
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public UUID getDispatchId() { return dispatchId; }
    public UUID getRideId() { return rideId; }
    public Long getDriverId() { return driverId; }
    public Instant getAcceptedAt() { return acceptedAt; }
}