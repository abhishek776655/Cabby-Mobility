package com.smartmobility.auth.service.Impl;

import com.smartmobility.auth.entity.RefreshToken;
import com.smartmobility.auth.exception.InvalidCredentialsException;
import com.smartmobility.auth.repository.RefreshTokenRepository;
import com.smartmobility.auth.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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

    @Override
    public RefreshToken create(Long userId) {

        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .token(UUID.randomUUID().toString())
                .expiryDate(LocalDateTime.now().plusDays(REFRESH_EXPIRY_DAYS))
                .revoked(false)
                .build();

        return repository.save(token);
    }

    @Override
    public RefreshToken validate(String token) {

        RefreshToken refreshToken = repository.findByToken(token)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        if (refreshToken.isRevoked() ||
                refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new InvalidCredentialsException("Refresh token expired or revoked");
        }

        return refreshToken;
    }

    @Override
    public void revoke(String token) {

        RefreshToken refreshToken = repository.findByToken(token)
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
