package com.smartmobility.pricing.service.impl;

import com.smartmobility.pricing.client.RoutingServiceClient;
import com.smartmobility.pricing.domain.FareCalculator;
import com.smartmobility.pricing.dto.FareFinalizeRequest;
import com.smartmobility.pricing.dto.FareFinalizeResponse;
import com.smartmobility.pricing.dto.Coordinate;
import com.smartmobility.pricing.dto.FareQuoteRequest;
import com.smartmobility.pricing.dto.FareQuoteResponse;
import com.smartmobility.pricing.dto.QuoteAllRequest;
import com.smartmobility.pricing.dto.QuoteAllResponse;
import com.smartmobility.pricing.entity.FareEstimateEntity;
import com.smartmobility.pricing.entity.RateCardEntity;
import com.smartmobility.pricing.exception.RateCardNotFoundException;
import com.smartmobility.pricing.redis.RateCardCacheService;
import com.smartmobility.pricing.redis.SurgeCacheService;
import com.smartmobility.pricing.repository.FareEstimateRepository;
import com.smartmobility.pricing.repository.RateCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingServiceImpl {

    private final RateCardRepository rateCardRepository;
    private final FareEstimateRepository fareEstimateRepository;
    private final RoutingServiceClient routingServiceClient;
    private final SurgeCacheService surgeCacheService;
    private final RateCardCacheService rateCardCacheService;

    /**
     * Compute and save a fare quote (upfront pricing).
     */
    @Transactional
    public FareQuoteResponse quote(FareQuoteRequest request) {
        RateCardEntity rateCard = loadRateCard(request.getVehicleType())
                .filter(RateCardEntity::isActive)
                .orElseThrow(() -> new RateCardNotFoundException("Active rate card not found for: " + request.getVehicleType()));

        double surgeMultiplier = surgeCacheService.getSurgeMultiplier("DEFAULT_ZONE"); // For v1, one global zone

        RoutingServiceClient.RouteData routeData = routingServiceClient.getRoute(
                request.getPickupLat(), request.getPickupLng(),
                request.getDropLat(), request.getDropLng()
        ).orElseGet(() -> fallbackHaversineRoute(
                request.getPickupLat(), request.getPickupLng(),
                request.getDropLat(), request.getDropLng()
        ));

        String estimateSource = routeData.polyline().equals("fallback") ? "FALLBACK" : "VALHALLA";

        FareCalculator.FareBreakdown breakdown = FareCalculator.calculate(
                (long) routeData.distanceMeters(),
                (long) routeData.durationSeconds(),
                rateCard,
                surgeMultiplier
        );

        FareEstimateEntity estimate = FareEstimateEntity.builder()
                .pickupLat(request.getPickupLat())
                .pickupLng(request.getPickupLng())
                .dropLat(request.getDropLat())
                .dropLng(request.getDropLng())
                .vehicleType(request.getVehicleType())
                .baseFare(breakdown.getBaseFare())
                .distanceFare(breakdown.getDistanceFare())
                .timeFare(breakdown.getTimeFare())
                .surgeAmount(breakdown.getSurgeAmount())
                .totalFare(breakdown.getTotal())
                .surgeMultiplier(breakdown.getSurgeMultiplier())
                .status("ESTIMATED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        estimate = fareEstimateRepository.save(estimate);

        return FareQuoteResponse.builder()
                .estimateId(estimate.getId())
                .breakdown(breakdown)
                .currency("INR") // Assuming INR for paise
                .estimateSource(estimateSource)
                .polyline(routeData.polyline())
                .coordinates(toDtoCoordinates(routeData.coordinates()))
                .distanceMeters(routeData.distanceMeters())
                .durationSeconds(routeData.durationSeconds())
                .build();
    }

    /**
     * Preview fares for every active vehicle type against one route — the
     * "enter destination, see all vehicle prices + the route line" screen.
     * Fetches the route from routing-service exactly once and reuses it for
     * every rate card; nothing is persisted (persistence happens when the
     * rider actually picks a vehicle type via {@link #quote}).
     */
    public QuoteAllResponse quoteAll(QuoteAllRequest request) {
        double surgeMultiplier = surgeCacheService.getSurgeMultiplier("DEFAULT_ZONE");

        RoutingServiceClient.RouteData routeData = routingServiceClient.getRoute(
                request.getPickupLat(), request.getPickupLng(),
                request.getDropLat(), request.getDropLng()
        ).orElseGet(() -> fallbackHaversineRoute(
                request.getPickupLat(), request.getPickupLng(),
                request.getDropLat(), request.getDropLng()
        ));

        String estimateSource = routeData.polyline().equals("fallback") ? "FALLBACK" : "VALHALLA";

        List<QuoteAllResponse.VehicleQuote> quotes = rateCardRepository.findAll().stream()
                .filter(RateCardEntity::isActive)
                .map(rateCard -> QuoteAllResponse.VehicleQuote.builder()
                        .vehicleType(rateCard.getVehicleType())
                        .breakdown(FareCalculator.calculate(
                                (long) routeData.distanceMeters(),
                                (long) routeData.durationSeconds(),
                                rateCard,
                                surgeMultiplier))
                        .build())
                .toList();

        return QuoteAllResponse.builder()
                .polyline(routeData.polyline())
                .coordinates(toDtoCoordinates(routeData.coordinates()))
                .distanceMeters(routeData.distanceMeters())
                .durationSeconds(routeData.durationSeconds())
                .estimateSource(estimateSource)
                .currency("INR")
                .quotes(quotes)
                .build();
    }

    private java.util.Optional<RateCardEntity> loadRateCard(String vehicleType) {
        return rateCardCacheService.get(vehicleType).or(() -> {
            java.util.Optional<RateCardEntity> fromDb = rateCardRepository.findById(vehicleType);
            fromDb.ifPresent(rc -> rateCardCacheService.put(vehicleType, rc));
            return fromDb;
        });
    }

    private static List<Coordinate> toDtoCoordinates(List<RoutingServiceClient.Coordinate> source) {
        if (source == null) {
            return List.of();
        }
        return source.stream()
                .map(c -> Coordinate.builder().lat(c.lat()).lng(c.lng()).build())
                .toList();
    }

    /**
     * Finalize the fare after a ride is completed.
     * In v1, we recalculate based on the original pickup/drop locations.
     */
    @Transactional
    public FareFinalizeResponse finalize(FareFinalizeRequest request) {
        FareEstimateEntity estimate = fareEstimateRepository.findById(request.getEstimateId())
                .orElseThrow(() -> new IllegalArgumentException("Estimate not found: " + request.getEstimateId()));

        if (!"ESTIMATED".equals(estimate.getStatus())) {
            throw new IllegalArgumentException("Estimate is already " + estimate.getStatus());
        }

        // V1 Finalize: re-run the quote logic to simulate actual trip distance calculation,
        // using the same rate card logic. In the real world, actual GPS distance would be passed here.
        RateCardEntity rateCard = loadRateCard(estimate.getVehicleType())
                .orElseThrow(() -> new RateCardNotFoundException("Rate card missing during finalize"));

        RoutingServiceClient.RouteData routeData = routingServiceClient.getRoute(
                estimate.getPickupLat(), estimate.getPickupLng(),
                estimate.getDropLat(), estimate.getDropLng()
        ).orElseGet(() -> fallbackHaversineRoute(
                estimate.getPickupLat(), estimate.getPickupLng(),
                estimate.getDropLat(), estimate.getDropLng()
        ));

        String calculationSource = routeData.polyline().equals("fallback") ? "FALLBACK" : "VALHALLA";

        FareCalculator.FareBreakdown finalBreakdown = FareCalculator.calculate(
                (long) routeData.distanceMeters(),
                (long) routeData.durationSeconds(),
                rateCard,
                estimate.getSurgeMultiplier() // lock in the surge from the estimate
        );

        estimate.setRideId(request.getRideId());
        estimate.setStatus("FINALIZED");
        estimate.setBaseFare(finalBreakdown.getBaseFare());
        estimate.setDistanceFare(finalBreakdown.getDistanceFare());
        estimate.setTimeFare(finalBreakdown.getTimeFare());
        estimate.setSurgeAmount(finalBreakdown.getSurgeAmount());
        estimate.setTotalFare(finalBreakdown.getTotal());
        estimate.setUpdatedAt(LocalDateTime.now());
        
        fareEstimateRepository.save(estimate);

        return FareFinalizeResponse.builder()
                .rideId(request.getRideId())
                .finalBreakdown(finalBreakdown)
                .currency("INR")
                .calculationSource(calculationSource)
                .build();
    }

    /**
     * Calculates distance using Haversine formula (meters) and assumes 30 km/h average speed.
     * Applies a 1.15x multiplier to distance to account for road curvature.
     */
    private RoutingServiceClient.RouteData fallbackHaversineRoute(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distanceKm = R * c;
        
        double paddedDistanceMeters = distanceKm * 1000 * 1.15; // 15% padding for road curves
        double durationSeconds = (paddedDistanceMeters / 1000.0) / 30.0 * 3600; // 30 km/h

        // Straight-line fallback has no real route shape — just the two endpoints,
        // so a map can still draw *something* rather than nothing.
        List<RoutingServiceClient.Coordinate> straightLine = List.of(
                new RoutingServiceClient.Coordinate(lat1, lon1),
                new RoutingServiceClient.Coordinate(lat2, lon2)
        );

        return new RoutingServiceClient.RouteData("fallback", straightLine, paddedDistanceMeters, durationSeconds);
    }
}
