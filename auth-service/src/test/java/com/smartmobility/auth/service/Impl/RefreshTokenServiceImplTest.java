package com.smartmobility.auth.service.Impl;

import com.smartmobility.auth.entity.RefreshToken;
import com.smartmobility.auth.exception.InvalidCredentialsException;
import com.smartmobility.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenServiceImpl(7L, repository);
    }

    @Test
    void createPersistsOnlyTheHashButReturnsTheRawToken() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);

        RefreshToken result = service.create(42L);

        verify(repository).save(saved.capture());
        String persistedHash = saved.getValue().getTokenHash();
        String rawToken = result.getToken();

        assertNotNull(rawToken);
        assertNotNull(persistedHash);
        assertNotEquals(rawToken, persistedHash, "raw token must never equal what gets persisted");
        assertEquals(64, persistedHash.length(), "SHA-256 hex digest is 64 chars");
    }

    @Test
    void validateHashesTheIncomingRawTokenToLookItUp() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        RefreshToken created = service.create(1L);
        verify(repository).save(saved.capture());
        String persistedHash = saved.getValue().getTokenHash();

        RefreshToken persisted = RefreshToken.builder()
                .userId(1L)
                .tokenHash(persistedHash)
                .expiryDate(LocalDateTime.now().plusDays(1))
                .revoked(false)
                .build();
        when(repository.findByTokenHash(persistedHash)).thenReturn(Optional.of(persisted));

        RefreshToken validated = service.validate(created.getToken());

        assertEquals(1L, validated.getUserId());
    }

    @Test
    void validateRejectsUnknownToken() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> service.validate("not-a-real-token"));
    }

    @Test
    void revokeMarksTheMatchingEntityRevoked() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        RefreshToken created = service.create(5L);
        verify(repository).save(saved.capture());
        String persistedHash = saved.getValue().getTokenHash();

        RefreshToken persisted = RefreshToken.builder()
                .userId(5L)
                .tokenHash(persistedHash)
                .revoked(false)
                .build();
        when(repository.findByTokenHash(persistedHash)).thenReturn(Optional.of(persisted));

        service.revoke(created.getToken());

        assertTrue(persisted.isRevoked());
    }
}
