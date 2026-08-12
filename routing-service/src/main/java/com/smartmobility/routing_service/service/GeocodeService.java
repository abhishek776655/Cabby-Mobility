package com.smartmobility.routing_service.service;

import com.smartmobility.routing_service.dto.GeocodeSuggestion;

import java.util.List;

public interface GeocodeService {

    /**
     * @param query    partial address text as typed by the rider
     * @param lat      optional bias point latitude (rider's current position), may be null
     * @param lon      optional bias point longitude, may be null
     * @param limit    max rows to return; clamped to the configured maximum
     */
    List<GeocodeSuggestion> autocomplete(String query, Double lat, Double lon, Integer limit);

    /**
     * Names the place at a coordinate, for a pin the rider dropped on the map.
     *
     * @return the nearest known place, or empty when the point falls outside the serviceable
     *         area (where no route could be produced anyway)
     */
    java.util.Optional<GeocodeSuggestion> reverse(double lat, double lon);
}
