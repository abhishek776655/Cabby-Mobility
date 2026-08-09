package com.smartmobility.routing_service.controller;

import com.smartmobility.routing_service.dto.MatrixRequest;
import com.smartmobility.routing_service.dto.MatrixResponse;
import com.smartmobility.routing_service.dto.RouteRequest;
import com.smartmobility.routing_service.dto.RouteResponse;
import com.smartmobility.routing_service.service.RoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class RoutingControllerTest {

    @Mock
    private RoutingService routingService;

    @InjectMocks
    private RoutingController routingController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(routingController).build();
    }

    @Test
    void testGetRoute_Success() throws Exception {
        RouteResponse response = RouteResponse.builder()
                .polyline("mock_polyline")
                .distanceMeters(5000)
                .durationSeconds(300)
                .build();

        when(routingService.getRoute(any(RouteRequest.class))).thenReturn(response);

        String requestJson = """
            {
              "originLat": 12.0,
              "originLng": 77.0,
              "destLat": 12.1,
              "destLng": 77.1
            }
        """;

        mockMvc.perform(post("/internal/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.polyline").value("mock_polyline"))
                .andExpect(jsonPath("$.data.distanceMeters").value(5000.0));
    }

    @Test
    void testGetMatrix_Success() throws Exception {
        MatrixResponse response = MatrixResponse.builder()
                .distancesMeters(List.of(List.of(1000.0, 2000.0)))
                .durationsSeconds(List.of(List.of(120.0, 240.0)))
                .build();

        when(routingService.getMatrix(any(MatrixRequest.class))).thenReturn(response);

        String requestJson = """
            {
              "sources": [{"lat": 12.0, "lng": 77.0}],
              "targets": [
                {"lat": 12.1, "lng": 77.1},
                {"lat": 12.2, "lng": 77.2}
              ]
            }
        """;

        mockMvc.perform(post("/internal/matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.distancesMeters[0][0]").value(1000.0))
                .andExpect(jsonPath("$.data.durationsSeconds[0][1]").value(240.0));
    }
}
