package com.smartmobility.routing_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "valhalla")
public class RoutingProperties {
    private String url;
    private int timeoutMs = 5000;
}
