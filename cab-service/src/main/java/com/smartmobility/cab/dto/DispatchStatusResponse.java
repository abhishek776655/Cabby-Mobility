package com.smartmobility.cab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchStatusResponse {

    private UUID dispatchId;
    private UUID rideId;
    private String status;
    private Long driverUserId;
    private int retryCount;
    private Instant createdAt;
    private Instant expiresAt;
}