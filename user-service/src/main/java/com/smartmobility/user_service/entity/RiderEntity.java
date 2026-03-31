package com.smartmobility.user_service.entity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "riders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiderEntity {
    @Id
    private Long userId;

    private Double rating;
}
