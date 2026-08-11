package com.smartmobility.cab.service.impl;

import com.smartmobility.cab.client.PricingServiceClient;
import com.smartmobility.cab.dto.RideRequestDTO;
import com.smartmobility.cab.entity.RideEntity;
import com.smartmobility.cab.kafka.RideEventProducer;
import com.smartmobility.cab.repository.ProcessedEventRepository;
import com.smartmobility.cab.repository.RideRepository;
import com.smartmobility.cab.security.RideAuthorizationGuard;
import com.smartmobility.cab.state.RideStateFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideServiceImplTest {

    @Mock
    private RideRepository rideRepository;

    @Mock
    private RideStateFactory rideStateFactory;

    @Mock
    private RideEventProducer producer;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private RideAuthorizationGuard authorizationGuard;

    @Mock
    private PricingServiceClient pricingServiceClient;

    private RideServiceImpl rideService;

    @BeforeEach
    void setUp() {
        rideService = new RideServiceImpl(
                rideRepository, rideStateFactory, producer, processedEventRepository,
                authorizationGuard, pricingServiceClient, new SimpleMeterRegistry());
    }

    @Test
    void createRidePassesRequestedVehicleTypeToPricingQuoteInsteadOfHardcodedStandard() {
        RideRequestDTO request = RideRequestDTO.builder()
                .riderUserId(1L)
                .pickupLocation("A").dropLocation("B")
                .pickupLatitude(12.0).pickupLongitude(77.0)
                .dropLatitude(12.1).dropLongitude(77.1)
                .vehicleType("PREMIUM")
                .build();

        when(rideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pricingServiceClient.quote(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString())).thenReturn(Optional.empty());

        rideService.createRide(request, 1L);

        ArgumentCaptor<String> vehicleTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(pricingServiceClient).quote(eq(12.0), eq(77.0), eq(12.1), eq(77.1), vehicleTypeCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("PREMIUM", vehicleTypeCaptor.getValue());
    }

    @Test
    void createRidePersistsVehicleTypeOnTheSavedRide() {
        RideRequestDTO request = RideRequestDTO.builder()
                .riderUserId(1L)
                .pickupLocation("A").dropLocation("B")
                .pickupLatitude(12.0).pickupLongitude(77.0)
                .dropLatitude(12.1).dropLongitude(77.1)
                .vehicleType("XL")
                .build();

        when(rideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pricingServiceClient.quote(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString())).thenReturn(Optional.empty());

        var response = rideService.createRide(request, 1L);

        org.junit.jupiter.api.Assertions.assertEquals("XL", response.getVehicleType());
    }
}
