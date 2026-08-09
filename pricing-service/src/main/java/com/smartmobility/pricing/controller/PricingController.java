package com.smartmobility.pricing.controller;

import com.smartmobility.pricing.dto.ApiResponse;
import com.smartmobility.pricing.dto.FareFinalizeRequest;
import com.smartmobility.pricing.dto.FareFinalizeResponse;
import com.smartmobility.pricing.dto.FareQuoteRequest;
import com.smartmobility.pricing.dto.FareQuoteResponse;
import com.smartmobility.pricing.dto.QuoteAllRequest;
import com.smartmobility.pricing.dto.QuoteAllResponse;
import com.smartmobility.pricing.entity.FareEstimateEntity;
import com.smartmobility.pricing.repository.FareEstimateRepository;
import com.smartmobility.pricing.service.impl.PricingServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/fares")
@RequiredArgsConstructor
public class PricingController {

    private final PricingServiceImpl pricingService;
    private final FareEstimateRepository fareEstimateRepository;

    @PostMapping("/quote")
    public ResponseEntity<ApiResponse<FareQuoteResponse>> quote(@Valid @RequestBody FareQuoteRequest request) {
        FareQuoteResponse response = pricingService.quote(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/quote-all")
    public ResponseEntity<ApiResponse<QuoteAllResponse>> quoteAll(@Valid @RequestBody QuoteAllRequest request) {
        QuoteAllResponse response = pricingService.quoteAll(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/finalize")
    public ResponseEntity<ApiResponse<FareFinalizeResponse>> finalizeFare(@Valid @RequestBody FareFinalizeRequest request) {
        FareFinalizeResponse response = pricingService.finalize(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{rideId}")
    public ResponseEntity<ApiResponse<FareEstimateEntity>> getFareEstimate(@PathVariable String rideId) {
        return fareEstimateRepository.findByRideId(rideId)
                .map(estimate -> ResponseEntity.ok(ApiResponse.success(estimate)))
                .orElseThrow(() -> new IllegalArgumentException("Fare estimate not found for ride: " + rideId));
    }
}
