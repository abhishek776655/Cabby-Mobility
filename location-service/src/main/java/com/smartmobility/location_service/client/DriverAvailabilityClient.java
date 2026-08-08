package com.smartmobility.location_service.client;

public interface DriverAvailabilityClient {

    void markAvailable(Long userId, boolean available);
}
