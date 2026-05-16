package com.smartmobility.location_service;

import com.smartmobility.location_service.exception.InvalidLocationException;
import com.smartmobility.location_service.exception.LocationServiceException;
import com.smartmobility.location_service.repository.LocationRepository;
import com.smartmobility.location_service.service.impl.LocationServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocationServiceImplTest {

    @Test
    void goOnlineStoresLocationAndMarksDriverOnline() {
        FakeLocationRepository locationRepository = new FakeLocationRepository();
        LocationServiceImpl locationService = new LocationServiceImpl(locationRepository);

        locationService.goOnline("42", 28.7041, 77.1025);

        assertEquals("42", locationRepository.lastUpsertDriverId);
        assertEquals(28.7041, locationRepository.lastLat);
        assertEquals(77.1025, locationRepository.lastLng);
        assertEquals("42", locationRepository.lastOnlineDriverId);
    }

    @Test
    void getNearbyDriversRejectsInvalidRadius() {
        LocationServiceImpl locationService = new LocationServiceImpl(new FakeLocationRepository());

        assertThrows(
                InvalidLocationException.class,
                () -> locationService.getNearbyDrivers(28.7041, 77.1025, 100.0, 10)
        );
    }

    @Test
    void getNearbyDriversReturnsRepositoryResults() {
        FakeLocationRepository locationRepository = new FakeLocationRepository();
        locationRepository.nearbyDrivers = List.of("42", "43");
        LocationServiceImpl locationService = new LocationServiceImpl(locationRepository);

        List<String> result = locationService.getNearbyDrivers(28.7041, 77.1025, 5.0, 10);

        assertEquals(List.of("42", "43"), result);
    }

    @Test
    void updateDriverLocationWrapsRepositoryFailure() {
        FakeLocationRepository locationRepository = new FakeLocationRepository();
        locationRepository.failUpsert = true;
        LocationServiceImpl locationService = new LocationServiceImpl(locationRepository);

        assertThrows(
                LocationServiceException.class,
                () -> locationService.updateDriverLocation("42", 28.7041, 77.1025)
        );
    }

    private static class FakeLocationRepository implements LocationRepository {

        private String lastUpsertDriverId;
        private double lastLat;
        private double lastLng;
        private String lastOnlineDriverId;
        private boolean failUpsert;
        private List<String> nearbyDrivers = List.of();

        @Override
        public void upsertDriverLocation(String driverId, double lat, double lng) {
            if (failUpsert) {
                throw new RuntimeException("redis unavailable");
            }
            this.lastUpsertDriverId = driverId;
            this.lastLat = lat;
            this.lastLng = lng;
        }

        @Override
        public void markDriverOnline(String driverId) {
            this.lastOnlineDriverId = driverId;
        }

        @Override
        public void markDriverOffline(String driverId) {
        }

        @Override
        public List<String> findNearbyDrivers(double lat, double lng, double radiusKm, int limit) {
            return nearbyDrivers;
        }
    }
}
