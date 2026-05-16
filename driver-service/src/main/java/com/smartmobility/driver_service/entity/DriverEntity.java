package com.smartmobility.driver_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "drivers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_driver_user_id", columnNames = "user_id")
        },
        indexes = {
                @Index(name = "idx_driver_user_id", columnList = "user_id"),
                @Index(name = "idx_driver_available", columnList = "available")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    @Builder.Default
    private Double rating = 5.0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean available = false;

    @Column(name = "vehicle_details")
    private String vehicleDetails;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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