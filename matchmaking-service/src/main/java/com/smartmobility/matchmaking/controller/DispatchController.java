package com.smartmobility.matchmaking.controller;

import com.smartmobility.matchmaking.dto.ApiResponse;
import com.smartmobility.matchmaking.dto.DispatchStatusResponse;
import com.smartmobility.matchmaking.service.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/internal/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;

    @GetMapping("/{rideId}")
    public ResponseEntity<ApiResponse<DispatchStatusResponse>> getDispatchStatus(
            @PathVariable UUID rideId) {

        return dispatchService.getDispatchStatus(rideId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)))
                .orElse(ResponseEntity.notFound().build());
    }
}