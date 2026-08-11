package com.smartmobility.routing_service.service.impl;

import com.smartmobility.routing_service.client.ValhallaClient;
import com.smartmobility.routing_service.client.valhalla.ValhallaRouteRequest;
import com.smartmobility.routing_service.client.valhalla.ValhallaRouteResponse;
import com.smartmobility.routing_service.dto.MatrixRequest;
import com.smartmobility.routing_service.dto.MatrixResponse;
import com.smartmobility.routing_service.dto.RouteRequest;
import com.smartmobility.routing_service.dto.RouteResponse;
import com.smartmobility.routing_service.exception.RouteNotFoundException;
import com.smartmobility.routing_service.mapper.PolylineDecoder;
import com.smartmobility.routing_service.redis.RouteCacheService;
import com.smartmobility.routing_service.service.RoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingServiceImpl implements RoutingService {

    private final ValhallaClient valhallaClient;
    private final RouteCacheService routeCacheService;

    @Override
    public RouteResponse getRoute(RouteRequest request) {
        Optional<RouteResponse> cached = routeCacheService.get(
            request.getOriginLat(), request.getOriginLng(),
            request.getDestLat(), request.getDestLng(), request.getCostingModel());
        if (cached.isPresent()) {
            log.debug("Route cache hit for {},{} -> {},{}",
                request.getOriginLat(), request.getOriginLng(), request.getDestLat(), request.getDestLng());
            return cached.get();
        }

        ValhallaRouteRequest valhallaRequest = ValhallaRouteRequest.builder()
                .locations(List.of(
                        ValhallaRouteRequest.Location.builder().lat(request.getOriginLat()).lon(request.getOriginLng()).build(),
                        ValhallaRouteRequest.Location.builder().lat(request.getDestLat()).lon(request.getDestLng()).build()
                ))
                .costing(request.getCostingModel())
                .units("kilometers")
                .build();

        ValhallaRouteResponse valhallaResponse = valhallaClient.getRoute(valhallaRequest);

        if (valhallaResponse == null || valhallaResponse.getTrip() == null || valhallaResponse.getTrip().getLegs() == null || valhallaResponse.getTrip().getLegs().isEmpty()) {
            throw new RouteNotFoundException("No path found between the provided locations.");
        }

        ValhallaRouteResponse.Trip trip = valhallaResponse.getTrip();
        
        List<RouteResponse.Leg> legs = trip.getLegs().stream().map(leg -> 
            RouteResponse.Leg.builder()
                .distanceMeters(leg.getSummary().getLength() * 1000)
                .durationSeconds(leg.getSummary().getTime())
                .build()
        ).collect(Collectors.toList());

        // We assume one leg for simple A to B
        String polyline = trip.getLegs().get(0).getShape();

        RouteResponse response = RouteResponse.builder()
                .polyline(polyline)
                .coordinates(PolylineDecoder.decode(polyline))
                .distanceMeters(trip.getSummary().getLength() * 1000)
                .durationSeconds(trip.getSummary().getTime())
                .legs(legs)
                .build();

        routeCacheService.put(request.getOriginLat(), request.getOriginLng(),
            request.getDestLat(), request.getDestLng(), request.getCostingModel(), response);

        return response;
    }

    @Override
    public MatrixResponse getMatrix(MatrixRequest request) {
        ValhallaRouteRequest valhallaRequest = ValhallaRouteRequest.builder()
                .sources(request.getSources().stream()
                        .map(loc -> ValhallaRouteRequest.Location.builder().lat(loc.getLat()).lon(loc.getLng()).build())
                        .collect(Collectors.toList()))
                .targets(request.getTargets().stream()
                        .map(loc -> ValhallaRouteRequest.Location.builder().lat(loc.getLat()).lon(loc.getLng()).build())
                        .collect(Collectors.toList()))
                .costing(request.getCostingModel())
                .units("kilometers")
                .build();

        ValhallaRouteResponse valhallaResponse = valhallaClient.getMatrix(valhallaRequest);

        if (valhallaResponse == null || valhallaResponse.getSources_to_targets() == null) {
            throw new RouteNotFoundException("No path found for matrix request.");
        }

        List<List<Double>> distances = valhallaResponse.getSources_to_targets().stream()
                .map(row -> row.stream()
                        .map(cell -> cell.getDistance() * 1000)
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());

        List<List<Double>> durations = valhallaResponse.getSources_to_targets().stream()
                .map(row -> row.stream()
                        .map(ValhallaRouteResponse.SourceToTarget::getTime)
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());

        return MatrixResponse.builder()
                .distancesMeters(distances)
                .durationsSeconds(durations)
                .build();
    }
}
