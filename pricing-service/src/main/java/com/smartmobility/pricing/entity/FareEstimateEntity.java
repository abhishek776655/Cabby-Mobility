package com.smartmobility.pricing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fare_estimates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareEstimateEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true)
    private String rideId;
    
    private double pickupLat;
    private double pickupLng;
    private double dropLat;
    private double dropLng;
    
    private String vehicleType;
    
    private long baseFare;
    private long distanceFare;
    private long timeFare;
    private long surgeAmount;
    private long totalFare;
    private double surgeMultiplier;
    
    private String status; // ESTIMATED, FINALIZED, EXPIRED
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
