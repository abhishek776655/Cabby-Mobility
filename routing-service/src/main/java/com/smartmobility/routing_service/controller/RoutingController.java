package com.smartmobility.routing_service.controller;

import com.smartmobility.routing_service.dto.ApiResponse;
import com.smartmobility.routing_service.dto.MatrixRequest;
import com.smartmobility.routing_service.dto.MatrixResponse;
import com.smartmobility.routing_service.dto.RouteRequest;
import com.smartmobility.routing_service.dto.RouteResponse;
import com.smartmobility.routing_service.service.RoutingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class RoutingController {

    private final RoutingService routingService;

    @PostMapping("/route")
    public ResponseEntity<ApiResponse<RouteResponse>> getRoute(@Valid @RequestBody RouteRequest request) {
        RouteResponse response = routingService.getRoute(request);
        return ResponseEntity.ok(ApiResponse.<RouteResponse>builder().success(true).data(response).build());
    }

    @PostMapping("/matrix")
    public ResponseEntity<ApiResponse<MatrixResponse>> getMatrix(@Valid @RequestBody MatrixRequest request) {
        MatrixResponse response = routingService.getMatrix(request);
        return ResponseEntity.ok(ApiResponse.<MatrixResponse>builder().success(true).data(response).build());
    }
}
