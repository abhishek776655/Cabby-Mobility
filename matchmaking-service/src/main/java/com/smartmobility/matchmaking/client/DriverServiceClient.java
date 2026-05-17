package com.smartmobility.matchmaking.client;

import com.smartmobility.matchmaking.dto.DriverResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DriverServiceClient {

    private final RestClient restClient;

    public DriverServiceClient(
            @Value("${services.driver.url}") String driverServiceUrl,
            RestClient restClient) {
        this.restClient = restClient.mutate().baseUrl(driverServiceUrl).build();
    }

    public DriverResponseDTO getDriver(Long userId) {
        return restClient.get()
                .uri("/drivers/{userId}", userId)
                .retrieve()
                .body(DriverResponseDTO.class);
    }

    public void markUnavailable(Long userId) {
        restClient.patch()
                .uri("/drivers/{userId}/availability?available=false", userId)
                .retrieve()
                .toBodilessEntity();
    }
}