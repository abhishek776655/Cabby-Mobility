package com.smartmobility.rider_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "riders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_rider_user_id", columnNames = "user_id")
        },
        indexes = {
                @Index(name = "idx_rider_user_id", columnList = "user_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;


    @Column(nullable = false)
    @Builder.Default
    private Double rating = 5.0;

    @Column(name = "preferred_payment_method", nullable = false)
    @Builder.Default
    private String preferredPaymentMethod = "CASH";

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
