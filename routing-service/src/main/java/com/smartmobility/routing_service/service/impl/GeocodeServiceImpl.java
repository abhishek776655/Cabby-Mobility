package com.smartmobility.routing_service.service.impl;

import com.smartmobility.routing_service.client.PhotonClient;
import com.smartmobility.routing_service.client.photon.PhotonResponse;
import com.smartmobility.routing_service.config.PhotonProperties;
import com.smartmobility.routing_service.dto.GeocodeSuggestion;
import com.smartmobility.routing_service.service.GeocodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodeServiceImpl implements GeocodeService {

    /**
     * One- and two-character queries match a large slice of the index and produce noise rather
     * than useful rows, so the client's early keystrokes are answered with an empty list
     * instead of a wasted round trip.
     */
    private static final int MIN_QUERY_LENGTH = 3;

    private final PhotonClient photonClient;
    private final PhotonProperties properties;

    @Override
    public List<GeocodeSuggestion> autocomplete(String query, Double lat, Double lon, Integer limit) {
        if (query == null || query.trim().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }

        double biasLat = lat != null ? lat : properties.getDefaultLat();
        double biasLon = lon != null ? lon : properties.getDefaultLon();
        int effectiveLimit = limit == null
                ? properties.getMaxResults()
                : Math.clamp(limit, 1, properties.getMaxResults());

        PhotonResponse response = photonClient.autocomplete(query.trim(), biasLat, biasLon, effectiveLimit);
        if (response == null || response.getFeatures() == null) {
            return List.of();
        }

        List<GeocodeSuggestion> suggestions = new ArrayList<>();
        for (PhotonResponse.Feature feature : response.getFeatures()) {
            GeocodeSuggestion suggestion = toSuggestion(feature);
            if (suggestion != null) {
                suggestions.add(suggestion);
            }
        }
        return suggestions;
    }

    @Override
    public Optional<GeocodeSuggestion> reverse(double lat, double lon) {
        // /reverse has no bbox parameter, so the serviceable-area guarantee that autocomplete
        // gets for free has to be enforced here — otherwise a rider could drop a pin outside
        // the routable region and only discover it when dispatch fails.
        if (!withinServiceArea(lat, lon)) {
            return Optional.empty();
        }
        PhotonResponse response = photonClient.reverse(lat, lon);
        if (response == null || response.getFeatures() == null || response.getFeatures().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(toSuggestion(response.getFeatures().get(0)));
    }

    private boolean withinServiceArea(double lat, double lon) {
        String[] parts = properties.getBbox().split(",");
        if (parts.length != 4) {
            log.warn("photon.bbox is malformed ('{}'); cannot validate service area", properties.getBbox());
            return false;
        }
        try {
            double minLon = Double.parseDouble(parts[0].trim());
            double minLat = Double.parseDouble(parts[1].trim());
            double maxLon = Double.parseDouble(parts[2].trim());
            double maxLat = Double.parseDouble(parts[3].trim());
            return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
        } catch (NumberFormatException e) {
            log.warn("photon.bbox is not numeric ('{}'); cannot validate service area", properties.getBbox(), e);
            return false;
        }
    }

    private GeocodeSuggestion toSuggestion(PhotonResponse.Feature feature) {
        if (feature == null || feature.getGeometry() == null || feature.getProperties() == null) {
            return null;
        }
        List<Double> coordinates = feature.getGeometry().getCoordinates();
        // GeoJSON is [lon, lat]; reading these positionally the other way round is the classic
        // way to end up with a pin in the wrong hemisphere.
        if (coordinates == null || coordinates.size() < 2) {
            return null;
        }
        double lon = coordinates.get(0);
        double lat = coordinates.get(1);

        PhotonResponse.Properties p = feature.getProperties();
        String label = firstNonBlank(p.getName(), p.getStreet(), p.getDistrict(), p.getCity());
        if (label == null) {
            return null;
        }

        // Build the secondary line from the fields that aren't already the label, so a row
        // doesn't read "Lajpat Nagar / Lajpat Nagar".
        String description = Stream.of(p.getHousenumber(), p.getStreet(), p.getDistrict(), p.getCity(), p.getState())
                .filter(value -> value != null && !value.isBlank())
                .filter(value -> !value.equalsIgnoreCase(label))
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        return GeocodeSuggestion.builder()
                .label(label)
                .description(description)
                .lat(lat)
                .lng(lon)
                .kind(p.getOsmValue())
                .build();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
