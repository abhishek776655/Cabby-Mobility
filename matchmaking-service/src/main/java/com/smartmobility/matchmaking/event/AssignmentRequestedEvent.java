package com.smartmobility.matchmaking.event;

import java.time.Instant;
import java.util.UUID;

public class AssignmentRequestedEvent {
    private String eventId;
    private String eventType = "ASSIGNMENT_REQUESTED";
    private UUID dispatchId;
    private UUID rideId;
    private Long driverId;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private String pickupLocation;
    private Instant expiresAt;

    public static AssignmentRequestedEventBuilder builder() {
        return new AssignmentRequestedEventBuilder();
    }

    public static class AssignmentRequestedEventBuilder {
        private AssignmentRequestedEvent event = new AssignmentRequestedEvent();

        public AssignmentRequestedEventBuilder eventId(String eventId) { event.eventId = eventId; return this; }
        public AssignmentRequestedEventBuilder dispatchId(UUID dispatchId) { event.dispatchId = dispatchId; return this; }
        public AssignmentRequestedEventBuilder rideId(UUID rideId) { event.rideId = rideId; return this; }
        public AssignmentRequestedEventBuilder driverId(Long driverId) { event.driverId = driverId; return this; }
        public AssignmentRequestedEventBuilder pickupLatitude(Double pickupLatitude) { event.pickupLatitude = pickupLatitude; return this; }
        public AssignmentRequestedEventBuilder pickupLongitude(Double pickupLongitude) { event.pickupLongitude = pickupLongitude; return this; }
        public AssignmentRequestedEventBuilder pickupLocation(String pickupLocation) { event.pickupLocation = pickupLocation; return this; }
        public AssignmentRequestedEventBuilder expiresAt(Instant expiresAt) { event.expiresAt = expiresAt; return this; }

        public AssignmentRequestedEvent build() {
            event.eventType = "ASSIGNMENT_REQUESTED";
            return event;
        }
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public UUID getDispatchId() { return dispatchId; }
    public UUID getRideId() { return rideId; }
    public Long getDriverId() { return driverId; }
    public Double getPickupLatitude() { return pickupLatitude; }
    public Double getPickupLongitude() { return pickupLongitude; }
    public String getPickupLocation() { return pickupLocation; }
    public Instant getExpiresAt() { return expiresAt; }
}