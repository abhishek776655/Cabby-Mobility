package com.smartmobility.user_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverEntity {

    @Id
    private Long userId;

    private String licenseNumber;

    private Boolean available;

    private Double rating;
}