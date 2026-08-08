package com.smartmobility.cab.client;

import com.smartmobility.cab.dto.DispatchStatusResponse;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Component
public class MatchmakingServiceClient {

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${services.matchmaking.url}")
    private String matchmakingUrl;

    @Value("${internal.api.secret}")
    private String internalApiSecret;

    public MatchmakingServiceClient(RestTemplate restTemplate, MeterRegistry meterRegistry) {
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
    }

    public DispatchStatusResponse getDispatchStatus(UUID rideId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            String url = matchmakingUrl + "/internal/dispatch/" + rideId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Secret", internalApiSecret);
            ResponseEntity<DispatchStatusResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), DispatchStatusResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            outcome = "empty";
        } catch (Exception e) {
            outcome = "error";
            log.warn("Failed to get dispatch status from matchmaking: {}", e.getMessage());
        } finally {
            recordDependencyMetric(sample, "matchmaking-service", "get-dispatch-status", outcome);
        }
        return null;
    }

    private void recordDependencyMetric(Timer.Sample sample, String dependency, String operation, String outcome) {
        sample.stop(Timer.builder("dependency.client.duration")
                .description("Duration of downstream service calls")
                .publishPercentileHistogram()
                .tag("dependency", dependency)
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(meterRegistry));
    }
}
