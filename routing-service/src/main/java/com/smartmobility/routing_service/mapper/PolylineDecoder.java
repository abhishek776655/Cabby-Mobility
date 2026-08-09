package com.smartmobility.routing_service.mapper;

import com.smartmobility.routing_service.dto.RouteResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes Valhalla's encoded shape strings into plain lat/lng points.
 * Valhalla's default shape precision is 1e6 (polyline6) — see
 * https://valhalla.github.io/valhalla/decoding/ — not the 1e5 precision used
 * by Google's Maps/Directions polyline encoding.
 */
public final class PolylineDecoder {

    private static final double PRECISION = 1e6;

    private PolylineDecoder() {
    }

    public static List<RouteResponse.Coordinate> decode(String encoded) {
        List<RouteResponse.Coordinate> coordinates = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) {
            return coordinates;
        }

        int index = 0;
        int length = encoded.length();
        int lat = 0;
        int lng = 0;

        while (index < length) {
            int latDelta = 0;
            int shift = 0;
            int b;
            do {
                if (index >= length) {
                    // Truncated/malformed input mid-coordinate — stop rather
                    // than run off the end of the string.
                    return coordinates;
                }
                b = encoded.charAt(index++) - 63;
                latDelta |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lat += (latDelta & 1) != 0 ? ~(latDelta >> 1) : (latDelta >> 1);

            int lngDelta = 0;
            shift = 0;
            do {
                if (index >= length) {
                    return coordinates;
                }
                b = encoded.charAt(index++) - 63;
                lngDelta |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lng += (lngDelta & 1) != 0 ? ~(lngDelta >> 1) : (lngDelta >> 1);

            coordinates.add(RouteResponse.Coordinate.builder()
                    .lat(lat / PRECISION)
                    .lng(lng / PRECISION)
                    .build());
        }

        return coordinates;
    }
}
