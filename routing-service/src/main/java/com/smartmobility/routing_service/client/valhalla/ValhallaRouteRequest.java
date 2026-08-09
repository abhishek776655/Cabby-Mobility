package com.smartmobility.routing_service.client.valhalla;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ValhallaRouteRequest {
    private List<Location> locations;
    private List<Location> sources;
    private List<Location> targets;
    private String costing;
    private String units; // usually "kilometers" for Valhalla

    @Data
    @Builder
    public static class Location {
        private Double lat;
        private Double lon;
    }
}
