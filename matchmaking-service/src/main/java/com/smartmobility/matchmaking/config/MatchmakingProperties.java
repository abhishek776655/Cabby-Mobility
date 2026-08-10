package com.smartmobility.matchmaking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "dispatch")
public class MatchmakingProperties {

    private Assignment assignment = new Assignment();
    private Discovery discovery = new Discovery();
    private Scoring scoring = new Scoring();

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
    public Discovery getDiscovery() { return discovery; }

    public static class Discovery {
        // Escalating search radii (km) tried in order before giving up.
        // First tier matches the previous fixed 5km default.
        private List<Double> radiusStepsKm = List.of(5.0, 10.0, 15.0);
        // Delay before the scheduler retries discovery at the next radius tier.
        private int sweepDelaySeconds = 8;

        public List<Double> getRadiusStepsKm() { return radiusStepsKm; }
        public void setRadiusStepsKm(List<Double> radiusStepsKm) { this.radiusStepsKm = radiusStepsKm; }
        public int getSweepDelaySeconds() { return sweepDelaySeconds; }
        public void setSweepDelaySeconds(int sweepDelaySeconds) { this.sweepDelaySeconds = sweepDelaySeconds; }
    }

    public long getOnTripReservationSeconds() { return onTripReservationSeconds; }
    public void setOnTripReservationSeconds(long onTripReservationSeconds) { this.onTripReservationSeconds = onTripReservationSeconds; }

    public static class Scoring {
        private double ratingWeight = 0.4;
        private double distanceWeight = 0.6;
        public double getRatingWeight() { return ratingWeight; }
        public void setRatingWeight(double ratingWeight) { this.ratingWeight = ratingWeight; }
        public double getDistanceWeight() { return distanceWeight; }
        public void setDistanceWeight(double distanceWeight) { this.distanceWeight = distanceWeight; }
    }

    public Scoring getScoring() { return scoring; }
}
