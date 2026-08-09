package com.smartmobility.routing_service.service.impl;

import com.smartmobility.routing_service.client.ValhallaClient;
import com.smartmobility.routing_service.client.valhalla.ValhallaRouteRequest;
import com.smartmobility.routing_service.client.valhalla.ValhallaRouteResponse;
import com.smartmobility.routing_service.dto.MatrixRequest;
import com.smartmobility.routing_service.dto.MatrixResponse;
import com.smartmobility.routing_service.dto.RouteRequest;
import com.smartmobility.routing_service.dto.RouteResponse;
import com.smartmobility.routing_service.exception.RouteNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RoutingServiceImplTest {

    @Mock
    private ValhallaClient valhallaClient;

    private RoutingServiceImpl routingService;

    @BeforeEach
    void setUp() {
        routingService = new RoutingServiceImpl(valhallaClient);
    }

    @Test
    void testGetRoute_Success() {
        // Arrange
        RouteRequest request = new RouteRequest();
        request.setOriginLat(12.0);
        request.setOriginLng(77.0);
        request.setDestLat(12.1);
        request.setDestLng(77.1);
        request.setCostingModel("auto");

        ValhallaRouteResponse mockResponse = new ValhallaRouteResponse();
        ValhallaRouteResponse.Trip trip = new ValhallaRouteResponse.Trip();
        ValhallaRouteResponse.Summary tripSummary = new ValhallaRouteResponse.Summary();
        tripSummary.setLength(5.5); // 5.5 km
        tripSummary.setTime(300.0); // 300 seconds
        trip.setSummary(tripSummary);
        
        ValhallaRouteResponse.Leg leg = new ValhallaRouteResponse.Leg();
        leg.setShape("mock_polyline");
        ValhallaRouteResponse.Summary legSummary = new ValhallaRouteResponse.Summary();
        legSummary.setLength(5.5);
        legSummary.setTime(300.0);
        leg.setSummary(legSummary);
        trip.setLegs(List.of(leg));
        
        mockResponse.setTrip(trip);

        when(valhallaClient.getRoute(any(ValhallaRouteRequest.class))).thenReturn(mockResponse);

        // Act
        RouteResponse response = routingService.getRoute(request);

        // Assert
        assertNotNull(response);
        assertEquals("mock_polyline", response.getPolyline());
        assertEquals(5500.0, response.getDistanceMeters()); // 5.5 * 1000
        assertEquals(300.0, response.getDurationSeconds());
        assertEquals(1, response.getLegs().size());
        assertEquals(5500.0, response.getLegs().get(0).getDistanceMeters());
    }

    @Test
    void testGetRoute_NotFound() {
        // Arrange
        RouteRequest request = new RouteRequest();
        request.setOriginLat(12.0);
        request.setOriginLng(77.0);
        request.setDestLat(12.1);
        request.setDestLng(77.1);

        ValhallaRouteResponse mockResponse = new ValhallaRouteResponse(); // Empty response
        when(valhallaClient.getRoute(any(ValhallaRouteRequest.class))).thenReturn(mockResponse);

        // Act & Assert
        assertThrows(RouteNotFoundException.class, () -> routingService.getRoute(request));
    }

    @Test
    void testGetMatrix_Success() {
        // Arrange
        MatrixRequest request = new MatrixRequest();
        MatrixRequest.Location source = new MatrixRequest.Location();
        source.setLat(12.0); source.setLng(77.0);
        request.setSources(List.of(source));

        MatrixRequest.Location target1 = new MatrixRequest.Location();
        target1.setLat(12.1); target1.setLng(77.1);
        MatrixRequest.Location target2 = new MatrixRequest.Location();
        target2.setLat(12.2); target2.setLng(77.2);
        request.setTargets(List.of(target1, target2));
        request.setCostingModel("auto");

        ValhallaRouteResponse mockResponse = new ValhallaRouteResponse();
        ValhallaRouteResponse.SourceToTarget cell1 = new ValhallaRouteResponse.SourceToTarget();
        cell1.setDistance(2.0); cell1.setTime(120.0);
        ValhallaRouteResponse.SourceToTarget cell2 = new ValhallaRouteResponse.SourceToTarget();
        cell2.setDistance(3.5); cell2.setTime(200.0);
        
        mockResponse.setSources_to_targets(List.of(List.of(cell1, cell2)));

        when(valhallaClient.getMatrix(any(ValhallaRouteRequest.class))).thenReturn(mockResponse);

        // Act
        MatrixResponse response = routingService.getMatrix(request);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getDistancesMeters().size());
        assertEquals(2, response.getDistancesMeters().get(0).size());
        assertEquals(2000.0, response.getDistancesMeters().get(0).get(0));
        assertEquals(3500.0, response.getDistancesMeters().get(0).get(1));
        assertEquals(120.0, response.getDurationsSeconds().get(0).get(0));
        assertEquals(200.0, response.getDurationsSeconds().get(0).get(1));
    }
}
