package com.smartmobility.matchmaking.client;

import com.smartmobility.matchmaking.dto.ApiResponse;
import com.smartmobility.matchmaking.dto.MatrixRequest;
import com.smartmobility.matchmaking.dto.MatrixResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RoutingServiceClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 250L;

    private final RestClient restClient;
    private final MeterRegistry meterRegistry;
    private final String internalApiSecret;

    public RoutingServiceClient(
            @Value("${services.routing.url}") String routingServiceUrl,
            @Value("${internal.api.secret}") String internalApiSecret,
            RestClient restClient,
            MeterRegistry meterRegistry) {
        this.restClient = restClient.mutate().baseUrl(routingServiceUrl).build();
        this.meterRegistry = meterRegistry;
        this.internalApiSecret = internalApiSecret;
    }

    @CircuitBreaker(name = "matchmaking-routing-get-durations", fallbackMethod = "getDurationsSecondsFallback")
    public Optional<List<Double>> getDurationsSeconds(double pickupLat, double pickupLng, List<MatrixRequest.Location> targets) {
        MatrixRequest request = MatrixRequest.builder()
                .sources(List.of(MatrixRequest.Location.builder().lat(pickupLat).lng(pickupLng).build()))
                .targets(targets)
                .costingModel("auto")
                .build();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt += 1) {
            Timer.Sample sample = Timer.start(meterRegistry);
            String outcome = "success";
            try {
                ApiResponse<MatrixResponse> response = restClient.post()
                        .uri("/internal/matrix")
                        .header("X-Internal-Secret", internalApiSecret)
                        .body(request)
                        .retrieve()
                        .body(new ParameterizedTypeReference<ApiResponse<MatrixResponse>>() {});

                if (response != null && response.getData() != null && response.getData().getDurationsSeconds() != null
                        && !response.getData().getDurationsSeconds().isEmpty()) {
                    return Optional.of(response.getData().getDurationsSeconds().get(0));
                }
                log.warn("Routing service returned an empty durations matrix on attempt {}", attempt);

            } catch (RestClientResponseException e) {
                outcome = e.getStatusCode().is5xxServerError() ? "server_error" : "client_error";
                boolean retryable = e.getStatusCode().is5xxServerError() && attempt < MAX_ATTEMPTS;
                log.error("Routing service returned an error on attempt {}: {} - {}",
                        attempt, e.getStatusCode(), e.getResponseBodyAsString(), e);
                if (!retryable) break;
            } catch (Exception e) {
                outcome = "error";
                boolean retryable = attempt < MAX_ATTEMPTS;
                log.error("Network error calling routing service on attempt {}: {}", attempt, e.getMessage(), e);
                if (!retryable) break;
            } finally {
                recordDependencyMetric(sample, "routing-service", "get-matrix", outcome);
            }

            try {
                TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Signal failure explicitly rather than fabricating durations: a fake uniform value
        // would make rankDrivers' sort produce an arbitrary order instead of a safe unranked
        // fallback. Caller must fall back to the pre-ranking driver order on empty.
        return Optional.empty();
    }

    Optional<List<Double>> getDurationsSecondsFallback(double pickupLat, double pickupLng, List<MatrixRequest.Location> targets, Throwable t) {
        log.warn("Circuit breaker open or getDurationsSeconds exhausted, returning empty: {}", t.getMessage());
        return Optional.empty();
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
