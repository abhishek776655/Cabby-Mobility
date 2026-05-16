package com.smartmobility.matchmaking.event;

import java.time.Instant;
import java.util.UUID;

public class AssignmentRejectedEvent {
    private String eventId;
    private String eventType = "ASSIGNMENT_REJECTED";
    private UUID dispatchId;
    private UUID rideId;
    private Long driverId;
    private String reason;
    private Instant rejectedAt;

    public static AssignmentRejectedEventBuilder builder() {
        return new AssignmentRejectedEventBuilder();
    }

    public static class AssignmentRejectedEventBuilder {
        private AssignmentRejectedEvent event = new AssignmentRejectedEvent();

        public AssignmentRejectedEventBuilder eventId(String eventId) { event.eventId = eventId; return this; }
        public AssignmentRejectedEventBuilder dispatchId(UUID dispatchId) { event.dispatchId = dispatchId; return this; }
        public AssignmentRejectedEventBuilder rideId(UUID rideId) { event.rideId = rideId; return this; }
        public AssignmentRejectedEventBuilder driverId(Long driverId) { event.driverId = driverId; return this; }
        public AssignmentRejectedEventBuilder reason(String reason) { event.reason = reason; return this; }
        public AssignmentRejectedEventBuilder rejectedAt(Instant rejectedAt) { event.rejectedAt = rejectedAt; return this; }

        public AssignmentRejectedEvent build() {
            event.eventType = "ASSIGNMENT_REJECTED";
            return event;
        }
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public UUID getDispatchId() { return dispatchId; }
    public UUID getRideId() { return rideId; }
    public Long getDriverId() { return driverId; }
    public String getReason() { return reason; }
    public Instant getRejectedAt() { return rejectedAt; }
}