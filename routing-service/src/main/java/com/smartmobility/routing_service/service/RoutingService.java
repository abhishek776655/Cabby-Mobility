package com.smartmobility.routing_service.service;

import com.smartmobility.routing_service.dto.MatrixRequest;
import com.smartmobility.routing_service.dto.MatrixResponse;
import com.smartmobility.routing_service.dto.RouteRequest;
import com.smartmobility.routing_service.dto.RouteResponse;

public interface RoutingService {
    RouteResponse getRoute(RouteRequest request);
    MatrixResponse getMatrix(MatrixRequest request);
}
