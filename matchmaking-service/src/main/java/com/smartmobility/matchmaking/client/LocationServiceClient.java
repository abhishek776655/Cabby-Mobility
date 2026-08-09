package com.smartmobility.matchmaking.client;

import com.smartmobility.matchmaking.dto.ApiResponse;
import com.smartmobility.matchmaking.dto.NearbyDriversRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LocationServiceClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 250L;

    private final RestClient restClient;
    private final MeterRegistry meterRegistry;
    private final String internalApiSecret;

    public LocationServiceClient(
            @Value("${services.location.url}") String locationServiceUrl,
            @Value("${internal.api.secret}") String internalApiSecret,
            RestClient restClient,
            MeterRegistry meterRegistry) {
        this.restClient = restClient.mutate().baseUrl(locationServiceUrl).build();
        this.meterRegistry = meterRegistry;
        this.internalApiSecret = internalApiSecret;
    }

    public List<Long> findNearbyDrivers(double latitude, double longitude, double radiusKm, int limit) {
        NearbyDriversRequest request = NearbyDriversRequest.builder()
                .lat(latitude)
                .lng(longitude)
                .radiusKm(radiusKm)
                .limit(limit)
                .build();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt += 1) {
            Timer.Sample sample = Timer.start(meterRegistry);
            String outcome = "success";
            try {
                ApiResponse<List<Long>> response = restClient.post()
                        .uri("/internal/nearby")
                        .header("X-Internal-Secret", internalApiSecret)
                        .body(request)
                        .retrieve()
                        .body(new ParameterizedTypeReference<ApiResponse<List<Long>>>() {});

                if (response == null || response.getData() == null) {
                    return List.of();
                }

                return response.getData();

            } catch (RestClientResponseException e) {
                outcome = e.getStatusCode().is5xxServerError() ? "server_error" : "client_error";
                boolean retryable = e.getStatusCode().is5xxServerError() && attempt < MAX_ATTEMPTS;
                log.error("Location service returned an error on attempt {}: {} - {}",
                        attempt, e.getStatusCode(), e.getResponseBodyAsString(), e);
                if (!retryable) {
                    return List.of();
                }
            } catch (Exception e) {
                outcome = "error";
                boolean retryable = attempt < MAX_ATTEMPTS;
                log.error("Network error calling location service on attempt {}: {}",
                        attempt, e.getMessage(), e);
                if (!retryable) {
                    return List.of();
                }
            } finally {
                recordDependencyMetric(sample, "location-service", "find-nearby-drivers", outcome);
            }

            try {
                TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return List.of();
            }
        }

        return List.of();
    }

    public List<com.smartmobility.matchmaking.dto.DriverLocationDTO> getDriverLocationsBatch(List<Long> driverUserIds) {
        if (driverUserIds == null || driverUserIds.isEmpty()) return List.of();
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            ApiResponse<List<com.smartmobility.matchmaking.dto.DriverLocationDTO>> response = restClient.post()
                    .uri("/internal/locations/batch")
                    .header("X-Internal-Secret", internalApiSecret)
                    .body(driverUserIds)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<List<com.smartmobility.matchmaking.dto.DriverLocationDTO>>>() {});

            return response != null && response.getData() != null ? response.getData() : List.of();
        } catch (Exception e) {
            outcome = "error";
            log.error("Failed to fetch driver locations batch: {}", e.getMessage(), e);
            return List.of();
        } finally {
            recordDependencyMetric(sample, "location-service", "get-locations-batch", outcome);
        }
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
