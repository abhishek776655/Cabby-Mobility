package com.smartmobility.location_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.*;

@Configuration
public class RedisOpsConfig {

    @Bean
    public GeoOperations<String, String> geoOps(RedisTemplate<String, String> redisTemplate) {
        return redisTemplate.opsForGeo();
    }

    @Bean
    public SetOperations<String, String> setOps(RedisTemplate<String, String> redisTemplate) {
        return redisTemplate.opsForSet();
    }
}
