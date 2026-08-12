package com.smartmobility.routing_service.client;

import com.smartmobility.routing_service.client.photon.PhotonResponse;
import com.smartmobility.routing_service.config.PhotonProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class PhotonClient {

    private final RestClient restClient;
    private final PhotonProperties properties;
    private final MeterRegistry meterRegistry;

    public PhotonClient(RestClient restClient, PhotonProperties properties, MeterRegistry meterRegistry) {
        this.restClient = restClient.mutate().baseUrl(properties.getUrl()).build();
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Autocomplete against the local photon index. Unlike {@link ValhallaClient} there is no
     * retry loop here: this sits on the keystroke path, where a slow answer is worse than no
     * answer — the next keystroke supersedes it anyway.
     */
    public PhotonResponse autocomplete(String query, double lat, double lon, int limit) {
        return call("autocomplete", uriBuilder -> uriBuilder
                .path("/api")
                .queryParam("q", query)
                // Enforced server-side; deliberately not caller-supplied.
                .queryParam("bbox", properties.getBbox())
                .queryParam("lat", lat)
                .queryParam("lon", lon)
                .queryParam("limit", limit)
                .queryParam("lang", "en")
                .build());
    }

    /**
     * Nearest known place to a coordinate — used to name a pin the rider dropped on the map.
     * There is no bbox parameter on photon's /reverse (it takes a point, not an area), so the
     * serviceable-area check happens in the service layer before this is called.
     */
    public PhotonResponse reverse(double lat, double lon) {
        return call("reverse", uriBuilder -> uriBuilder
                .path("/reverse")
                .queryParam("lat", lat)
                .queryParam("lon", lon)
                .queryParam("limit", 1)
                .queryParam("lang", "en")
                .build());
    }

    private PhotonResponse call(String operation, java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uriFn) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            return restClient.get().uri(uriFn).retrieve().body(PhotonResponse.class);
        } catch (RestClientResponseException e) {
            outcome = e.getStatusCode().is5xxServerError() ? "server_error" : "client_error";
            log.error("Photon returned {} for {}: {}", e.getStatusCode(), operation,
                    e.getResponseBodyAsString(), e);
            throw e;
        } catch (Exception e) {
            outcome = "error";
            log.error("Network error calling Photon {}: {}", operation, e.getMessage(), e);
            throw e;
        } finally {
            sample.stop(Timer.builder("dependency.client.duration")
                    .description("Duration of downstream service calls")
                    .publishPercentileHistogram()
                    .tag("dependency", "photon")
                    .tag("operation", operation)
                    .tag("outcome", outcome)
                    .register(meterRegistry));
        }
    }
}
