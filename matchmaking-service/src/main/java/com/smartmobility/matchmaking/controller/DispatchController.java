package com.smartmobility.matchmaking.controller;

import com.smartmobility.matchmaking.dto.ApiResponse;
import com.smartmobility.matchmaking.dto.CancelDispatchRequest;
import com.smartmobility.matchmaking.dto.DispatchStatusResponse;
import com.smartmobility.matchmaking.dto.DriverResponseRequest;
import com.smartmobility.matchmaking.service.DispatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;

    @PostMapping("/driver-response")
    public ResponseEntity<ApiResponse<Void>> handleDriverResponse(
            @Valid @RequestBody DriverResponseRequest request) {
        
        log.info("Driver {} responded {} to dispatch {}", 
            request.getDriverId(), request.getResponse(), request.getDispatchId());

        boolean accepted = request.getResponse() == DriverResponseRequest.DriverResponse.ACCEPT;
        dispatchService.handleDriverResponse(request.getDispatchId(), request.getDriverId(), accepted);
        
        String message = accepted ? "Assignment accepted" : "Assignment rejected";
        return ResponseEntity.ok(ApiResponse.success(null, message));
    }

    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelDispatch(
            @Valid @RequestBody CancelDispatchRequest request) {
        
        log.info("Cancelling dispatch for ride {}", request.getRideId());
        dispatchService.cancelDispatch(request.getRideId(), request.getReason());
        
        return ResponseEntity.ok(ApiResponse.success(null, "Dispatch cancelled"));
    }

    @GetMapping("/{rideId}")
    public ResponseEntity<ApiResponse<DispatchStatusResponse>> getDispatchStatus(
            @PathVariable UUID rideId) {
        
        return dispatchService.getDispatchStatus(rideId)
            .map(response -> ResponseEntity.ok(ApiResponse.success(response)))
            .orElse(ResponseEntity.notFound().build());
    }
}