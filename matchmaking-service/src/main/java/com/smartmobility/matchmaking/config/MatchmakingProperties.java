package com.smartmobility.matchmaking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dispatch")
public class MatchmakingProperties {

    private Assignment assignment = new Assignment();

    /**
     * Safety-net TTL (seconds) for a driver's reservation once they accept a ride, covering
     * the expected ride duration. The reservation is normally released explicitly when
     * ride-completed/ride-cancelled arrives; this TTL only protects against that event being
     * lost, so a driver isn't reserved forever.
     */
    private long onTripReservationSeconds = 7200;

    public static class Assignment {
        private int timeoutSeconds = 30;
        private int maxRetries = 10;
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }

    public int getDispatchTimeoutSeconds() {
        return assignment.getTimeoutSeconds();
    }

    public int getDispatchMaxRetries() {
        return assignment.getMaxRetries();
    }

    public Assignment getAssignment() { return assignment; }

    public long getOnTripReservationSeconds() { return onTripReservationSeconds; }
    public void setOnTripReservationSeconds(long onTripReservationSeconds) { this.onTripReservationSeconds = onTripReservationSeconds; }
}
