package com.smartmobility.auth.service.Impl;

import com.smartmobility.auth.client.UserServiceClient;
import com.smartmobility.auth.dto.ApiResponse;
import com.smartmobility.auth.dto.AuthResponseDTO;
import com.smartmobility.auth.dto.RegisterRequestDTO;
import com.smartmobility.auth.dto.UserResponseDTO;
import com.smartmobility.auth.entity.AuthCredential;
import com.smartmobility.auth.entity.RefreshToken;
import com.smartmobility.auth.entity.Role;
import com.smartmobility.auth.mapper.AuthMapper;
import com.smartmobility.auth.mapper.RefreshTokenMapper;
import com.smartmobility.auth.repository.AuthCredentialRepository;
import com.smartmobility.auth.repository.OutboxEventRepository;
import com.smartmobility.auth.service.RefreshTokenService;
import com.smartmobility.auth.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.Response;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthCredentialRepository authCredentialRepository;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private RefreshTokenMapper refreshTokenMapper;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuthServiceImpl newService() {
        return new AuthServiceImpl(
                passwordEncoder,
                authCredentialRepository,
                authMapper,
                new JwtUtil("mySecretKeyForDevelopmentOnly12345678901234567890", 86400000L),
                userServiceClient,
                refreshTokenService,
                refreshTokenMapper,
                meterRegistry,
                outboxEventRepository,
                objectMapper
        );
    }

    private static ApiResponse<UserResponseDTO> userResponse(Long userId, String email) {
        return ApiResponse.<UserResponseDTO>builder()
                .success(true)
                .data(UserResponseDTO.builder()
                        .userId(userId)
                        .email(email)
                        .roles(Set.of(Role.RIDER))
                        .build())
                .message("Success")
                .status(HttpStatus.OK.value())
                .build();
    }

    private static FeignException conflictException() {
        Request request = Request.create(
                Request.HttpMethod.POST,
                "/internal/users",
                Map.<String, Collection<String>>of(),
                null,
                StandardCharsets.UTF_8,
                null
        );

        Response response = Response.builder()
                .status(409)
                .reason("Conflict")
                .request(request)
                .headers(Map.<String, Collection<String>>of())
                .body("{\"message\":\"User already exists\"}", StandardCharsets.UTF_8)
                .build();

        return FeignException.errorStatus("UserServiceClient#createUser", response);
    }

    private static FeignException notFoundException() {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "/internal/users?email=sim.rider@example.com",
                Map.<String, Collection<String>>of(),
                null,
                StandardCharsets.UTF_8,
                null
        );

        Response response = Response.builder()
                .status(404)
                .reason("Not Found")
                .request(request)
                .headers(Map.<String, Collection<String>>of())
                .body("{\"message\":\"User not found\"}", StandardCharsets.UTF_8)
                .build();

        return FeignException.errorStatus("UserServiceClient#findByEmail", response);
    }

    @Test
    void register_shouldLoadExistingUserWhenCreateReturnsConflict() {
        AuthServiceImpl service = newService();
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .email("sim.rider@example.com")
                .password("secret")
                .roles(Set.of(Role.RIDER))
                .build();

        AuthCredential credential = AuthCredential.builder()
                .email(request.getEmail())
                .passwordHash("hashed")
                .build();

        when(authCredentialRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userServiceClient.createUser(any())).thenThrow(conflictException());
        when(userServiceClient.findByEmail(request.getEmail())).thenReturn(userResponse(42L, request.getEmail()));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed");
        when(authMapper.toEntity(request, "hashed")).thenReturn(credential);
        when(authCredentialRepository.save(credential)).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenService.create(42L)).thenReturn(RefreshToken.builder().token("refresh-token").userId(42L).build());
        when(authMapper.toDTO(any(AuthCredential.class), any(String.class), eq("refresh-token")))
                .thenReturn(AuthResponseDTO.builder().accessToken("access-token").refreshToken("refresh-token").userId(42L).build());

        AuthResponseDTO response = service.register(request);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(42L, response.getUserId());

        verify(userServiceClient).createUser(any());
        verify(userServiceClient).findByEmail(request.getEmail());
    }

    @Test
    void register_shouldRetryFindingExistingUserAfterConflictWhenLookupIsEventuallyVisible() {
        AuthServiceImpl service = newService();
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .email("sim.rider@example.com")
                .password("secret")
                .roles(Set.of(Role.RIDER))
                .build();

        AuthCredential credential = AuthCredential.builder()
                .email(request.getEmail())
                .passwordHash("hashed")
                .build();

        AtomicInteger lookupAttempts = new AtomicInteger();

        when(authCredentialRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userServiceClient.createUser(any())).thenThrow(conflictException());
        when(userServiceClient.findByEmail(request.getEmail())).thenAnswer(invocation -> {
            if (lookupAttempts.getAndIncrement() < 2) {
                throw notFoundException();
            }
            return userResponse(42L, request.getEmail());
        });
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed");
        when(authMapper.toEntity(request, "hashed")).thenReturn(credential);
        when(authCredentialRepository.save(credential)).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenService.create(42L)).thenReturn(RefreshToken.builder().token("refresh-token").userId(42L).build());
        when(authMapper.toDTO(any(AuthCredential.class), any(String.class), eq("refresh-token")))
                .thenReturn(AuthResponseDTO.builder().accessToken("access-token").refreshToken("refresh-token").userId(42L).build());

        AuthResponseDTO response = service.register(request);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(42L, response.getUserId());

        assertEquals(3, lookupAttempts.get());
    }

    @Test
    void register_shouldCreateUserWhenMissing() {
        AuthServiceImpl service = newService();
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .email("new.rider@example.com")
                .password("secret")
                .roles(Set.of(Role.RIDER))
                .build();

        AuthCredential credential = AuthCredential.builder()
                .email(request.getEmail())
                .passwordHash("hashed")
                .build();

        when(authCredentialRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userServiceClient.createUser(any())).thenReturn(userResponse(99L, request.getEmail()));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed");
        when(authMapper.toEntity(request, "hashed")).thenReturn(credential);
        when(authCredentialRepository.save(credential)).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenService.create(99L)).thenReturn(RefreshToken.builder().token("refresh-token").userId(99L).build());
        when(authMapper.toDTO(any(AuthCredential.class), any(String.class), eq("refresh-token")))
                .thenReturn(AuthResponseDTO.builder().accessToken("access-token").refreshToken("refresh-token").userId(99L).build());

        AuthResponseDTO response = service.register(request);

        assertNotNull(response);
        assertEquals(99L, response.getUserId());

        verify(userServiceClient).createUser(any());
    }

    @Test
    void register_shouldReturnTokenWhenAuthCredentialAlreadyExistsAndProfileNeedsRehydration() {
        AuthServiceImpl service = newService();
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .email("existing.rider@example.com")
                .password("secret")
                .roles(Set.of(Role.RIDER))
                .build();

        AuthCredential existingCredential = AuthCredential.builder()
                .email(request.getEmail())
                .passwordHash("secret")
                .userId(77L)
                .build();

        when(authCredentialRepository.existsByEmail(request.getEmail())).thenReturn(true);
        when(authCredentialRepository.findByEmail(request.getEmail())).thenReturn(java.util.Optional.of(existingCredential));
        when(passwordEncoder.matches(request.getPassword(), existingCredential.getPasswordHash())).thenReturn(true);
        when(userServiceClient.createUser(any())).thenReturn(userResponse(77L, request.getEmail()));
        when(refreshTokenService.create(77L)).thenReturn(RefreshToken.builder().token("refresh-token").userId(77L).build());
        when(authMapper.toDTO(any(AuthCredential.class), any(String.class), eq("refresh-token")))
                .thenReturn(AuthResponseDTO.builder().accessToken("access-token").refreshToken("refresh-token").userId(77L).build());

        AuthResponseDTO response = service.register(request);

        assertNotNull(response);
        assertEquals(77L, response.getUserId());

        verify(userServiceClient).createUser(any());
    }
}
