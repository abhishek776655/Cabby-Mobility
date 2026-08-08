package com.smartmobility.matchmaking.config;

import com.smartmobility.matchmaking.redis.ReservationService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusinessMetricsConfig {

    @Bean
    public Gauge driversReservedGauge(MeterRegistry meterRegistry, ReservationService reservationService) {
        return Gauge.builder("business.drivers.reserved", reservationService, ReservationService::countActiveReservations)
                .description("Drivers currently holding an offer-window or on-trip reservation")
                .register(meterRegistry);
    }
}
