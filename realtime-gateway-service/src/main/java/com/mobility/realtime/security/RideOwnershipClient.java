package com.mobility.realtime.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class RideOwnershipClient {

    private final RestClient cabServiceRestClient;

    public RideOwnershipClient(RestClient cabServiceRestClient) {
        this.cabServiceRestClient = cabServiceRestClient;
    }

    /**
     * True if {@code userId} is the rider or driver on {@code rideId}, per cab-service's own
     * ownership check (GET /rides/{rideId} enforces this and 403s otherwise).
     */
    public boolean isRideParticipant(String rideId, Long userId) {
        try {
            cabServiceRestClient.get()
                    .uri("/rides/{rideId}", rideId)
                    .header("X-User-Id", String.valueOf(userId))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            return false;
        } catch (Exception e) {
            log.error("Failed to verify ride ownership for rideId={} userId={}", rideId, userId, e);
            return false;
        }
    }
}
