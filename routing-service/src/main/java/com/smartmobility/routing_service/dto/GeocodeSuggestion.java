package com.smartmobility.routing_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One autocomplete row, already flattened for display so the mobile client doesn't have to
 * reassemble OSM's field soup (name/street/housenumber/district/city) itself.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeocodeSuggestion {
    /** Primary line, e.g. "Qutub Minar Complex". */
    private String label;
    /** Secondary line, e.g. "Baba Shrichand Marg, South Delhi". Empty when nothing to add. */
    private String description;
    private double lat;
    private double lng;
    /** Raw OSM value ("monument", "suburb", ...) so the client can pick an icon. */
    private String kind;
}
