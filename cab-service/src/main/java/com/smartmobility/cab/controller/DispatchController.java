package com.smartmobility.cab.controller;

import com.smartmobility.cab.dto.ApiResponse;
import com.smartmobility.cab.dto.ApiResponseBuilder;
import com.smartmobility.cab.dto.CancelDispatchRequest;
import com.smartmobility.cab.dto.DispatchStatusResponse;
import com.smartmobility.cab.dto.DriverResponseRequest;
import com.smartmobility.cab.service.DispatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;

    @PostMapping("/driver-response")
    public ResponseEntity<ApiResponse<Void>> handleDriverResponse(
            @Valid @RequestBody DriverResponseRequest request) {

        boolean accepted = request.getResponse() == DriverResponseRequest.DriverResponse.ACCEPT;
        dispatchService.handleDriverResponse(
                request.getDispatchId(),
                request.getDriverUserId(),
                accepted
        );

        String message = accepted ? "Assignment accepted" : "Assignment rejected";
        return ResponseEntity.ok(ApiResponseBuilder.success(null, message));
    }

    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelDispatch(
            @Valid @RequestBody CancelDispatchRequest request) {

        dispatchService.cancelDispatch(request.getRideId(), request.getReason());
        return ResponseEntity.ok(ApiResponseBuilder.success(null, "Dispatch cancelled"));
    }

    @GetMapping("/{rideId}")
    public ResponseEntity<ApiResponse<DispatchStatusResponse>> getDispatchStatus(
            @PathVariable UUID rideId) {

        return dispatchService.getDispatchStatus(rideId)
                .map(response -> ResponseEntity.ok(
                        ApiResponseBuilder.success(response, "Dispatch status fetched")
                ))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
