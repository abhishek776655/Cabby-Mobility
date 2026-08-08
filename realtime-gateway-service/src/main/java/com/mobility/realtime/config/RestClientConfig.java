package com.mobility.realtime.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient cabServiceRestClient(@Value("${services.cab.url}") String cabServiceUrl) {
        return RestClient.builder().baseUrl(cabServiceUrl).build();
    }
}
