package com.smartmobility.pricing.service;

import com.smartmobility.pricing.client.RoutingServiceClient;
import com.smartmobility.pricing.dto.FareFinalizeRequest;
import com.smartmobility.pricing.dto.FareFinalizeResponse;
import com.smartmobility.pricing.dto.FareQuoteRequest;
import com.smartmobility.pricing.dto.FareQuoteResponse;
import com.smartmobility.pricing.entity.FareEstimateEntity;
import com.smartmobility.pricing.entity.RateCardEntity;
import com.smartmobility.pricing.redis.SurgeCacheService;
import com.smartmobility.pricing.repository.FareEstimateRepository;
import com.smartmobility.pricing.repository.RateCardRepository;
import com.smartmobility.pricing.service.impl.PricingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PricingServiceImplTest {

    @Mock
    private RateCardRepository rateCardRepository;

    @Mock
    private FareEstimateRepository fareEstimateRepository;

    @Mock
    private RoutingServiceClient routingServiceClient;

    @Mock
    private SurgeCacheService surgeCacheService;

    @Mock
    private com.smartmobility.pricing.redis.RateCardCacheService rateCardCacheService;

    private PricingServiceImpl pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingServiceImpl(rateCardRepository, fareEstimateRepository, routingServiceClient, surgeCacheService, rateCardCacheService);
        lenient().when(rateCardCacheService.get(any())).thenReturn(Optional.empty());
    }

    @Test
    void testQuote_Success() {
        // Arrange
        FareQuoteRequest request = new FareQuoteRequest(12.0, 77.0, 12.1, 77.1, "STANDARD");
        RateCardEntity rateCard = RateCardEntity.builder()
                .vehicleType("STANDARD")
                .baseFare(5000)
                .perKmRate(1500)
                .perMinRate(200)
                .minFare(8000)
                .active(true)
                .build();

        when(rateCardRepository.findById("STANDARD")).thenReturn(Optional.of(rateCard));
        when(surgeCacheService.getSurgeMultiplier(anyString())).thenReturn(1.5);
        
        RoutingServiceClient.RouteData routeData = new RoutingServiceClient.RouteData("polyline", List.of(), 5000.0, 900.0);
        when(routingServiceClient.getRoute(12.0, 77.0, 12.1, 77.1)).thenReturn(Optional.of(routeData));
        
        FareEstimateEntity savedEntity = new FareEstimateEntity();
        savedEntity.setId(UUID.randomUUID());
        when(fareEstimateRepository.save(any(FareEstimateEntity.class))).thenReturn(savedEntity);

        // Act
        FareQuoteResponse response = pricingService.quote(request);

        // Assert
        assertNotNull(response);
        assertEquals("VALHALLA", response.getEstimateSource());
        assertEquals(savedEntity.getId(), response.getEstimateId());
        
        // Base: 5000
        // Dist (5km): 7500
        // Time (15m): 3000
        // Subtotal: 15500
        // Surge (1.5x): +7750
        // Total: 23250
        assertEquals(23250, response.getBreakdown().getTotal());
        assertEquals(7750, response.getBreakdown().getSurgeAmount());

        verify(fareEstimateRepository).save(any());
    }

    @Test
    void testQuote_FallbackHaversine() {
        // Arrange
        FareQuoteRequest request = new FareQuoteRequest(12.0, 77.0, 12.1, 77.1, "STANDARD");
        RateCardEntity rateCard = RateCardEntity.builder()
                .vehicleType("STANDARD")
                .baseFare(5000)
                .perKmRate(1500)
                .perMinRate(200)
                .minFare(8000)
                .active(true)
                .build();

        when(rateCardRepository.findById("STANDARD")).thenReturn(Optional.of(rateCard));
        when(surgeCacheService.getSurgeMultiplier(anyString())).thenReturn(1.0);
        
        // Routing fails
        when(routingServiceClient.getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());
        
        FareEstimateEntity savedEntity = new FareEstimateEntity();
        savedEntity.setId(UUID.randomUUID());
        when(fareEstimateRepository.save(any(FareEstimateEntity.class))).thenReturn(savedEntity);

        // Act
        FareQuoteResponse response = pricingService.quote(request);

        // Assert
        assertNotNull(response);
        assertEquals("FALLBACK", response.getEstimateSource());
    }
}
