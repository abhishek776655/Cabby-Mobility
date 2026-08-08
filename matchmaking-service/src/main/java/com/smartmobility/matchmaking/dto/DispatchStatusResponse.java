package com.smartmobility.matchmaking.dto;

import com.smartmobility.matchmaking.domain.DispatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchStatusResponse {

    private UUID dispatchId;
    private UUID rideId;
    private DispatchStatus status;
    private Long driverUserId;
    private Integer retryCount;
    private Instant createdAt;
    private Instant expiresAt;
}