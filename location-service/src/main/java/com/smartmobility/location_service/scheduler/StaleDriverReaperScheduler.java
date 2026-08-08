package com.smartmobility.location_service.scheduler;

import com.smartmobility.location_service.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StaleDriverReaperScheduler {

    private final LocationRepository locationRepository;

    @Scheduled(fixedDelay = 60000)
    public void evictStaleDrivers() {
        locationRepository.evictStaleDrivers();
    }
}
