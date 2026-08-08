package com.smartmobility.location_service;

import com.smartmobility.location_service.exception.InvalidLocationException;
import com.smartmobility.location_service.exception.LocationServiceException;
import com.smartmobility.location_service.client.DriverAvailabilityClient;
import com.smartmobility.location_service.repository.LocationRepository;
import com.smartmobility.location_service.exception.ForbiddenAccessException;
import com.smartmobility.location_service.security.DriverOwnershipGuard;
import com.smartmobility.location_service.service.impl.LocationServiceImpl;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationServiceImplTest {

    @Test
    void goOnlineStoresLocationAndMarksDriverOnline() {
        FakeLocationRepository locationRepository = new FakeLocationRepository();
        FakeDriverServiceClient driverServiceClient = new FakeDriverServiceClient();
        LocationServiceImpl locationService = new LocationServiceImpl(
                locationRepository,
                driverServiceClient,
                new DriverOwnershipGuard(),
                new SimpleMeterRegistry()
        );

        locationService.goOnline(42L, 42L, 28.7041, 77.1025);

        assertTrue(driverServiceClient.lastAvailable);
        assertEquals("42", locationRepository.lastUpsertDriverUserId);
        assertEquals(28.7041, locationRepository.lastLat);
        assertEquals(77.1025, locationRepository.lastLng);
        assertEquals("42", locationRepository.lastOnlineDriverUserId);
    }

    @Test
    void getNearbyDriversRejectsInvalidRadius() {
        LocationServiceImpl locationService = new LocationServiceImpl(
                new FakeLocationRepository(),
                new FakeDriverServiceClient(),
                new DriverOwnershipGuard(),
                new SimpleMeterRegistry()
        );

        assertThrows(
                InvalidLocationException.class,
                () -> locationService.getNearbyDrivers(28.7041, 77.1025, 100.0, 10)
        );
    }

    @Test
    void getNearbyDriversReturnsRepositoryResults() {
        FakeLocationRepository locationRepository = new FakeLocationRepository();
        locationRepository.nearbyDrivers = List.of("42", "43");
        LocationServiceImpl locationService = new LocationServiceImpl(
                locationRepository,
                new FakeDriverServiceClient(),
                new DriverOwnershipGuard(),
                new SimpleMeterRegistry()
        );

        List<Long> result = locationService.getNearbyDrivers(28.7041, 77.1025, 5.0, 10);

        assertEquals(List.of(42L, 43L), result);
    }

    @Test
    void getNearbyDrivers_returnsOnlyFromAvailableGeo() {
        FakeLocationRepository locationRepository = new FakeLocationRepository();
        locationRepository.nearbyDrivers = List.of("42", "43");
        LocationServiceImpl locationService = new LocationServiceImpl(
                locationRepository,
                new FakeDriverServiceClient(),
                new DriverOwnershipGuard(),
                new SimpleMeterRegistry()
        );

        List<Long> result = locationService.getNearbyDrivers(40.7128, -74.0060, 5.0, 10);

        assertEquals(2, result.size());
        assertTrue(result.contains(42L));
        assertTrue(result.contains(43L));
    }

    @Test
    void updateDriverLocationWrapsRepositoryFailure() {
        FakeLocationRepository locationRepository = new FakeLocationRepository();
        locationRepository.failUpsert = true;
        LocationServiceImpl locationService = new LocationServiceImpl(
                locationRepository,
                new FakeDriverServiceClient(),
                new DriverOwnershipGuard(),
                new SimpleMeterRegistry()
        );

        assertThrows(
                LocationServiceException.class,
                () -> locationService.updateDriverLocation(42L, 42L, 28.7041, 77.1025)
        );
    }

    @Test
    void goOfflineMarksDriverUnavailableInDriverService() {
        FakeLocationRepository locationRepository = new FakeLocationRepository();
        FakeDriverServiceClient driverServiceClient = new FakeDriverServiceClient();
        LocationServiceImpl locationService = new LocationServiceImpl(
                locationRepository,
                driverServiceClient,
                new DriverOwnershipGuard(),
                new SimpleMeterRegistry()
        );

        locationService.goOffline(42L, 42L);

        assertTrue(!driverServiceClient.lastAvailable);
        assertEquals("42", locationRepository.lastOfflineDriverUserId);
    }

    @Test
    void goOnlineRejectsDifferentCaller() {
        LocationServiceImpl locationService = new LocationServiceImpl(
                new FakeLocationRepository(),
                new FakeDriverServiceClient(),
                new DriverOwnershipGuard(),
                new SimpleMeterRegistry()
        );

        assertThrows(
                ForbiddenAccessException.class,
                () -> locationService.goOnline(42L, 7L, 28.7041, 77.1025)
        );
    }

    private static class FakeLocationRepository implements LocationRepository {

        private String lastUpsertDriverUserId;
        private double lastLat;
        private double lastLng;
        private String lastOnlineDriverUserId;
        private String lastOfflineDriverUserId;
        private boolean failUpsert;
        private List<String> nearbyDrivers = List.of();

        @Override
        public void upsertDriverLocation(String driverUserId, double lat, double lng) {
            if (failUpsert) {
                throw new RuntimeException("redis unavailable");
            }
            this.lastUpsertDriverUserId = driverUserId;
            this.lastLat = lat;
            this.lastLng = lng;
        }

        @Override
        public void markDriverOnline(String driverUserId) {
            this.lastOnlineDriverUserId = driverUserId;
        }

        @Override
        public void markDriverOffline(String driverUserId) {
            this.lastOfflineDriverUserId = driverUserId;
        }

        @Override
        public List<String> findNearbyDrivers(double lat, double lng, double radiusKm, int limit) {
            return nearbyDrivers;
        }

        @Override
        public void evictStaleDrivers() {
            // no-op for these tests
        }

        @Override
        public Long countOnlineDrivers() {
            return 0L;
        }
    }

    private static class FakeDriverServiceClient implements DriverAvailabilityClient {

        private boolean lastAvailable;

        @Override
        public void markAvailable(Long userId, boolean available) {
            this.lastAvailable = available;
        }
    }
}
