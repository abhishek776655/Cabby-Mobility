package com.smartmobility.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    /**
     * The raw, unhashed token — never persisted. Populated only in-memory right after
     * {@code create()} generates it, so callers can hand the raw value to the client while
     * only its hash ever reaches the database.
     */
    @Transient
    private String token;

    private LocalDateTime expiryDate;

    private boolean revoked;
}
