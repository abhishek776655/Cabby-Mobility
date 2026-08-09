package com.smartmobility.routing_service.client;

import com.smartmobility.routing_service.client.valhalla.ValhallaRouteRequest;
import com.smartmobility.routing_service.client.valhalla.ValhallaRouteResponse;
import com.smartmobility.routing_service.config.RoutingProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ValhallaClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 250L;

    private final RestClient restClient;
    private final MeterRegistry meterRegistry;

    public ValhallaClient(RestClient restClient, RoutingProperties properties, MeterRegistry meterRegistry) {
        this.restClient = restClient.mutate().baseUrl(properties.getUrl()).build();
        this.meterRegistry = meterRegistry;
    }

    public ValhallaRouteResponse getRoute(ValhallaRouteRequest request) {
        return callWithRetry("/route", "get-route", request);
    }

    public ValhallaRouteResponse getMatrix(ValhallaRouteRequest request) {
        return callWithRetry("/sources_to_targets", "get-matrix", request);
    }

    private ValhallaRouteResponse callWithRetry(String uri, String operation, ValhallaRouteRequest request) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt += 1) {
            Timer.Sample sample = Timer.start(meterRegistry);
            String outcome = "success";
            try {
                log.debug("Calling Valhalla {} with request: {}", uri, request);
                return restClient.post()
                        .uri(uri)
                        .body(request)
                        .retrieve()
                        .body(ValhallaRouteResponse.class);
            } catch (RestClientResponseException e) {
                outcome = e.getStatusCode().is5xxServerError() ? "server_error" : "client_error";
                boolean retryable = e.getStatusCode().is5xxServerError() && attempt < MAX_ATTEMPTS;
                log.error("Valhalla returned an error on attempt {} for {}: {} - {}",
                        attempt, uri, e.getStatusCode(), e.getResponseBodyAsString(), e);
                if (!retryable) {
                    throw e;
                }
            } catch (Exception e) {
                outcome = "error";
                boolean retryable = attempt < MAX_ATTEMPTS;
                log.error("Network error calling Valhalla {} on attempt {}: {}", uri, attempt, e.getMessage(), e);
                if (!retryable) {
                    throw e;
                }
            } finally {
                recordDependencyMetric(sample, operation, outcome);
            }

            try {
                TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while retrying Valhalla call", interruptedException);
            }
        }
        throw new IllegalStateException("Unreachable: retry loop exhausted without returning or throwing");
    }

    private void recordDependencyMetric(Timer.Sample sample, String operation, String outcome) {
        sample.stop(Timer.builder("dependency.client.duration")
                .description("Duration of downstream service calls")
                .publishPercentileHistogram()
                .tag("dependency", "valhalla")
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(meterRegistry));
    }
}
