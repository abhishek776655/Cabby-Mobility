package com.smartmobility.matchmaking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dispatch")
public class MatchmakingProperties {

    private Assignment assignment = new Assignment();
    private Reservation reservation = new Reservation();

    public static class Assignment {
        private int timeoutSeconds = 15;
        private int maxRetries = 10;
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }

    public static class Reservation {
        private int ttlSeconds = 15;
        public int getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    }

    public Assignment getAssignment() { return assignment; }
    public Reservation getReservation() { return reservation; }
}