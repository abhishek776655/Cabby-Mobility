package com.smartmobility.cab.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table( name = "rides")
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID riderId;

    private Long driverId;

    @Column(nullable = false)
    private String pickupLocation;

    @Column(nullable = false)
    private String dropLocation;

    @Column(nullable = false)
    private Double pickupLatitude;

    @Column(nullable = false)
    private Double pickupLongitude;

    @Column(nullable = false)
    private Double dropLatitude;

    @Column(nullable = false)
    private Double dropLongitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RideStatus status;

    private Double fare;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
