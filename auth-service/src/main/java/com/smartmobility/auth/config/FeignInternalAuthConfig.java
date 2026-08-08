package com.smartmobility.auth.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignInternalAuthConfig {

    @Value("${internal.api.secret}")
    private String internalApiSecret;

    @Bean
    public RequestInterceptor internalApiSecretInterceptor() {
        return requestTemplate -> requestTemplate.header("X-Internal-Secret", internalApiSecret);
    }
}
