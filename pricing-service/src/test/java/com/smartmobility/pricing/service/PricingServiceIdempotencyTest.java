package com.smartmobility.pricing.service;

import com.smartmobility.pricing.client.RoutingServiceClient;
import com.smartmobility.pricing.dto.FareQuoteRequest;
import com.smartmobility.pricing.dto.FareQuoteResponse;
import com.smartmobility.pricing.entity.FareEstimateEntity;
import com.smartmobility.pricing.redis.RateCardCacheService;
import com.smartmobility.pricing.redis.SurgeCacheService;
import com.smartmobility.pricing.repository.FareEstimateRepository;
import com.smartmobility.pricing.repository.RateCardRepository;
import com.smartmobility.pricing.service.impl.PricingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingServiceIdempotencyTest {

    @Mock private RateCardRepository rateCardRepository;
    @Mock private FareEstimateRepository fareEstimateRepository;
    @Mock private RoutingServiceClient routingServiceClient;
    @Mock private SurgeCacheService surgeCacheService;
    @Mock private RateCardCacheService rateCardCacheService;

    private PricingServiceImpl pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingServiceImpl(rateCardRepository, fareEstimateRepository, routingServiceClient, surgeCacheService, rateCardCacheService);
    }

    @Test
    void repeatedQuoteWithSameIdempotencyKeyReturnsExistingEstimateWithoutInsertingAgain() {
        UUID existingId = UUID.randomUUID();
        FareEstimateEntity existing = FareEstimateEntity.builder()
            .id(existingId).idempotencyKey("idem-key-1").vehicleType("STANDARD")
            .baseFare(5000).distanceFare(3000).timeFare(1000).surgeAmount(0).totalFare(9000)
            .surgeMultiplier(1.0).status("ESTIMATED").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
        when(fareEstimateRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.of(existing));

        FareQuoteRequest request = FareQuoteRequest.builder()
            .pickupLat(12.0).pickupLng(77.0).dropLat(12.1).dropLng(77.1)
            .vehicleType("STANDARD").idempotencyKey("idem-key-1")
            .build();

        FareQuoteResponse response = pricingService.quote(request);

        assertEquals(existingId, response.getEstimateId());
        assertEquals(9000, response.getBreakdown().getTotal());
        verify(fareEstimateRepository, never()).save(any());
        verifyNoInteractions(routingServiceClient);
        verifyNoInteractions(rateCardRepository);
    }
}
