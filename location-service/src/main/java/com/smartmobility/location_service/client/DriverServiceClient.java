package com.smartmobility.location_service.client;

import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class DriverServiceClient implements DriverAvailabilityClient {

    private final RestClient restClient;
    private final MeterRegistry meterRegistry;

    public DriverServiceClient(
            RestClient restClient,
            MeterRegistry meterRegistry,
            @Value("${services.driver.url}") String driverServiceUrl
    ) {
        this.restClient = restClient.mutate().baseUrl(driverServiceUrl).build();
        this.meterRegistry = meterRegistry;
    }

    public void markAvailable(Long userId, boolean available) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            restClient.patch()
                    .uri("/drivers/" + userId + "/availability?available=" + available)
                    .header("X-User-Id", String.valueOf(userId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            outcome = "error";
            log.error("Failed to update driver {} availability to {}: {}", userId, available, e.getMessage(), e);
            throw e;
        } finally {
            recordDependencyMetric(sample, "driver-service", "mark-available", outcome);
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
