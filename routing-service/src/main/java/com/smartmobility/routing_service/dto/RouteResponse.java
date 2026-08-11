package com.smartmobility.routing_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {
    // Encoded polyline6 string, kept for clients that already decode it
    // themselves (e.g. via @mapbox/polyline or Android's PolyUtil).
    private String polyline;
    // Same route, pre-decoded into plain [{lat,lng}, ...] for clients that
    // just want to plot it without a polyline-decoding library.
    private List<Coordinate> coordinates;
    private double distanceMeters;
    private double durationSeconds;
    private List<Leg> legs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Leg {
        private double distanceMeters;
        private double durationSeconds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coordinate {
        private double lat;
        private double lng;
    }
}
