package com.smartmobility.pricing.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "rate_cards")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateCardEntity {
    
    @Id
    private String vehicleType;
    
    private long baseFare;
    private long perKmRate;
    private long perMinRate;
    private long minFare;
    private long cancellationFee;
    
    private boolean active;
    private LocalDateTime updatedAt;
}
