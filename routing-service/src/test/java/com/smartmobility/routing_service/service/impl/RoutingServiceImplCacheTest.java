package com.smartmobility.routing_service.service.impl;

import com.smartmobility.routing_service.client.ValhallaClient;
import com.smartmobility.routing_service.dto.RouteRequest;
import com.smartmobility.routing_service.dto.RouteResponse;
import com.smartmobility.routing_service.redis.RouteCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingServiceImplCacheTest {

    @Mock
    private ValhallaClient valhallaClient;

    @Mock
    private RouteCacheService routeCacheService;

    private RoutingServiceImpl routingService;

    @BeforeEach
    void setUp() {
        routingService = new RoutingServiceImpl(valhallaClient, routeCacheService);
    }

    @Test
    void cacheHitSkipsValhallaCall() {
        RouteRequest request = new RouteRequest();
        request.setOriginLat(12.9716);
        request.setOriginLng(77.5946);
        request.setDestLat(12.9352);
        request.setDestLng(77.6244);
        request.setCostingModel("auto");

        RouteResponse cached = RouteResponse.builder()
            .polyline("cached").coordinates(List.of())
            .distanceMeters(4600.0).durationSeconds(980.0)
            .legs(List.of())
            .build();

        when(routeCacheService.get(12.9716, 77.5946, 12.9352, 77.6244, "auto")).thenReturn(Optional.of(cached));

        RouteResponse result = routingService.getRoute(request);

        assertEquals("cached", result.getPolyline());
        verifyNoInteractions(valhallaClient);
    }
}
