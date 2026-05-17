package com.smartmobility.matchmaking.client;

import com.smartmobility.matchmaking.dto.ApiResponse;
import com.smartmobility.matchmaking.dto.NearbyDriversRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class LocationServiceClient {

    private final RestClient restClient;

    public LocationServiceClient(
            @Value("${services.location.url}") String locationServiceUrl,
            RestClient restClient) {
        this.restClient = restClient.mutate().baseUrl(locationServiceUrl).build();
    }

    public List<Long> findNearbyDrivers(double latitude, double longitude, double radiusKm, int limit) {
        NearbyDriversRequest request = NearbyDriversRequest.builder()
                .lat(latitude)
                .lng(longitude)
                .radiusKm(radiusKm)
                .limit(limit)
                .build();

        ApiResponse<List<Long>> response = restClient.post()
                .uri("/internal/location/nearby")
                .header("X-Internal-Service", "matchmaking-service")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<Long>>>() {});

        if (response == null || response.getData() == null) {
            return List.of();
        }

        return response.getData();
    }
}
