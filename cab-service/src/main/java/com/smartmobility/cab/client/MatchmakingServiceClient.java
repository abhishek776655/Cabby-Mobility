package com.smartmobility.cab.client;

import com.smartmobility.cab.dto.DispatchStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Component
public class MatchmakingServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.matchmaking.url}")
    private String matchmakingUrl;

    public MatchmakingServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public DispatchStatusResponse getDispatchStatus(UUID rideId) {
        try {
            String url = matchmakingUrl + "/internal/dispatch/" + rideId;
            ResponseEntity<DispatchStatusResponse> response = restTemplate.getForEntity(url, DispatchStatusResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.warn("Failed to get dispatch status from matchmaking: {}", e.getMessage());
        }
        return null;
    }
}