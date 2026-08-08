package com.smartmobility.auth.service.Impl;

import com.smartmobility.auth.entity.RefreshToken;
import com.smartmobility.auth.exception.InvalidCredentialsException;
import com.smartmobility.auth.repository.RefreshTokenRepository;
import com.smartmobility.auth.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository repository;

    private final long REFRESH_EXPIRY_DAYS;
    public RefreshTokenServiceImpl(
            @Value("${jwt.refresh.expiry-days}") long refreshExpiryDays,
            RefreshTokenRepository repository
    ) {
        this.REFRESH_EXPIRY_DAYS = refreshExpiryDays;
        this.repository = repository;
    }

    /**
     * Only the hash is ever persisted, so a DB leak doesn't hand out usable refresh tokens.
     * SHA-256 (not bcrypt) is appropriate here: the token itself is already 128 bits of
     * random entropy (UUID), so this isn't a low-entropy secret needing slow-hash defense —
     * it just needs to not be stored in plaintext, and callers need a fast deterministic
     * lookup by hash.
     */
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public RefreshToken create(Long userId) {

        String rawToken = UUID.randomUUID().toString();

        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash(rawToken))
                .expiryDate(LocalDateTime.now().plusDays(REFRESH_EXPIRY_DAYS))
                .revoked(false)
                .build();

        RefreshToken saved = repository.save(token);
        saved.setToken(rawToken);
        return saved;
    }

    @Override
    public RefreshToken validate(String token) {

        RefreshToken refreshToken = repository.findByTokenHash(hash(token))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        if (refreshToken.isRevoked() ||
                refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new InvalidCredentialsException("Refresh token expired or revoked");
        }

        return refreshToken;
    }

    @Override
    public void revoke(String token) {

        RefreshToken refreshToken = repository.findByTokenHash(hash(token))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        refreshToken.setRevoked(true);
        repository.save(refreshToken);
    }

    @Override
    public void revokeAll(Long userId) {

        List<RefreshToken> tokens = repository.findAll()
                .stream()
                .filter(t -> t.getUserId().equals(userId))
                .toList();

        tokens.forEach(t -> t.setRevoked(true));

        repository.saveAll(tokens);
    }
}
