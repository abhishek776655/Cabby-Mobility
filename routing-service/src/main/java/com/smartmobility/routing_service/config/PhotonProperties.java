package com.smartmobility.routing_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "photon")
public class PhotonProperties {
    private String url;
    /**
     * Serviceable area as "minLon,minLat,maxLon,maxLat". Enforced on every query so a caller
     * cannot surface an address outside the routable region — see application.properties.
     */
    private String bbox;
    private double defaultLat;
    private double defaultLon;
    private int maxResults = 8;
}
