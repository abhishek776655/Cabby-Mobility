package com.smartmobility.routing_service.client.valhalla;

import lombok.Data;
import java.util.List;

@Data
public class ValhallaRouteResponse {
    private Trip trip;
    private List<List<SourceToTarget>> sources_to_targets;

    @Data
    public static class Trip {
        private Summary summary;
        private List<Leg> legs;
    }

    @Data
    public static class Summary {
        private double length; // default km
        private double time; // seconds
    }

    @Data
    public static class Leg {
        private String shape;
        private Summary summary;
    }

    @Data
    public static class SourceToTarget {
        private double distance;
        private double time;
    }
}
