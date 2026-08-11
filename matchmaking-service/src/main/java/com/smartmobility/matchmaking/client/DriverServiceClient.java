package com.smartmobility.matchmaking.client;

import com.smartmobility.matchmaking.dto.DriverResponseDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;
import com.smartmobility.matchmaking.dto.ApiResponse;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DriverServiceClient {

    private final RestClient restClient;
    private final MeterRegistry meterRegistry;

    public DriverServiceClient(
            @Value("${services.driver.url}") String driverServiceUrl,
            RestClient restClient,
            MeterRegistry meterRegistry) {
        this.restClient = restClient.mutate().baseUrl(driverServiceUrl).build();
        this.meterRegistry = meterRegistry;
    }

    @CircuitBreaker(name = "matchmaking-driver-get-driver", fallbackMethod = "getDriverFallback")
    public DriverResponseDTO getDriver(Long userId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            ApiResponse<DriverResponseDTO> response = restClient.get()
                    .uri("/drivers/internal/{userId}", userId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<DriverResponseDTO>>() {});

            return response != null ? response.getData() : null;
        } catch (Exception e) {
            outcome = "error";
            log.error("Failed to fetch driver {} details from Driver Service: {}", userId, e.getMessage(), e);
            return null;
        } finally {
            recordDependencyMetric(sample, "driver-service", "get-driver", outcome);
        }
    }

    @CircuitBreaker(name = "matchmaking-driver-batch", fallbackMethod = "getDriversBatchFallback")
    public List<DriverResponseDTO> getDriversBatch(List<Long> userIds) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            ApiResponse<List<DriverResponseDTO>> response = restClient.post()
                    .uri("/drivers/batch")
                    .body(userIds)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<List<DriverResponseDTO>>>() {});
            return response != null && response.getData() != null ? response.getData() : List.of();
        } catch (Exception e) {
            outcome = "error";
            log.error("Failed to fetch driver batch details from Driver Service: {}", e.getMessage(), e);
            return List.of();
        } finally {
            recordDependencyMetric(sample, "driver-service", "get-drivers-batch", outcome);
        }
    }

    public void markUnavailable(Long userId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            restClient.patch()
                    .uri("/drivers/internal/{userId}/availability?available=false", userId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            outcome = "error";
            log.error("Failed to mark driver {} as unavailable: {}", userId, e.getMessage(), e);
            throw e;
        } finally {
            recordDependencyMetric(sample, "driver-service", "mark-unavailable", outcome);
        }
    }

    DriverResponseDTO getDriverFallback(Long userId, Throwable t) {
        log.warn("Circuit breaker open or getDriver exhausted for driver {}, returning null: {}", userId, t.getMessage());
        return null;
    }

    List<DriverResponseDTO> getDriversBatchFallback(List<Long> userIds, Throwable t) {
        log.warn("Circuit breaker open or getDriversBatch exhausted, returning empty: {}", t.getMessage());
        return List.of();
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
