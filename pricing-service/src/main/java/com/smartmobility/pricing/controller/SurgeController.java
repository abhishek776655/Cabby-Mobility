package com.smartmobility.pricing.controller;

import com.smartmobility.pricing.dto.ApiResponse;
import com.smartmobility.pricing.redis.SurgeCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/surge")
@RequiredArgsConstructor
public class SurgeController {

    private final SurgeCacheService surgeCacheService;

    @PutMapping("/{zoneId}")
    public ResponseEntity<ApiResponse<Void>> updateSurge(
            @PathVariable String zoneId,
            @RequestParam double multiplier,
            @RequestParam(defaultValue = "600") long ttlSeconds) { // default 10 mins
        
        surgeCacheService.setSurgeMultiplier(zoneId, multiplier, ttlSeconds);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
