package com.smartmobility.routing_service.client.photon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Photon speaks GeoJSON: a FeatureCollection whose geometry is always a Point for our queries,
 * with the human-readable parts living in `properties`. Only the fields we surface are mapped;
 * photon returns many more (extent, osm_id, countrycode, ...) that we deliberately ignore.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PhotonResponse {

    private List<Feature> features;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Feature {
        private Geometry geometry;
        private Properties properties;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Geometry {
        /** GeoJSON order is [longitude, latitude] — NOT lat/lng. */
        private List<Double> coordinates;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Properties {
        private String name;
        private String street;
        private String housenumber;
        private String district;
        private String city;
        private String state;
        private String postcode;
        private String countrycode;
        // Photon emits these snake_cased; Spring Boot's default ObjectMapper does not
        // translate, so they must be bound explicitly or they silently stay null.
        /** e.g. "monument", "suburb", "station" — useful for picking a list icon. */
        @JsonProperty("osm_value")
        private String osmValue;
        @JsonProperty("osm_key")
        private String osmKey;
    }
}
