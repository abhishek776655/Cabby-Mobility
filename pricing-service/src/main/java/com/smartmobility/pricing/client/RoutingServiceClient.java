package com.smartmobility.pricing.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class RoutingServiceClient {

    private final RestClient restClient;
    private final MeterRegistry meterRegistry;
    private final String routingServiceUrl;
    private final String internalApiSecret;
    
    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MS = 250;

    public RoutingServiceClient(
            RestClient restClient,
            MeterRegistry meterRegistry,
            @Value("${services.routing.url}") String routingServiceUrl,
            @Value("${internal.api.secret}") String internalApiSecret) {
        this.restClient = restClient;
        this.meterRegistry = meterRegistry;
        this.routingServiceUrl = routingServiceUrl;
        this.internalApiSecret = internalApiSecret;
    }

    public record RouteRequest(double originLat, double originLng, double destLat, double destLng, String costingModel) {}

    public record RouteResponse(boolean success, RouteData data) {}

    public record RouteData(String polyline, List<Coordinate> coordinates, double distanceMeters, double durationSeconds) {}

    public record Coordinate(double lat, double lng) {}

    @CircuitBreaker(name = "routing-service-get-route", fallbackMethod = "getRouteFallback")
    public Optional<RouteData> getRoute(double originLat, double originLng, double destLat, double destLng) {
        Timer.Sample sample = Timer.start(meterRegistry);
        RouteRequest requestBody = new RouteRequest(originLat, originLng, destLat, destLng, "auto");
        
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                RouteResponse response = restClient.post()
                        .uri(routingServiceUrl + "/internal/route")
                        .header("X-Internal-Secret", internalApiSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(RouteResponse.class);
                        
                if (response != null && response.success() && response.data() != null) {
                    sample.stop(meterRegistry.timer("client.routing.getRoute", "status", "success"));
                    return Optional.of(response.data());
                }
            } catch (RestClientException e) {
                log.warn("Attempt {}/{} failed to get route from routing-service: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(BACKOFF_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        sample.stop(meterRegistry.timer("client.routing.getRoute", "status", "failure"));
        log.error("All {} attempts to get route failed", MAX_ATTEMPTS);
        return Optional.empty();
    }

    Optional<RouteData> getRouteFallback(double originLat, double originLng, double destLat, double destLng, Throwable t) {
        log.warn("Circuit breaker open or getRoute exhausted, returning empty: {}", t.getMessage());
        return Optional.empty();
    }
}
