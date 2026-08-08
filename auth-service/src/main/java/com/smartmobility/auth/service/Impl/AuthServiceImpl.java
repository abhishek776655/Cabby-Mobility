package com.smartmobility.auth.service.Impl;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.smartmobility.auth.client.UserServiceClient;
import com.smartmobility.auth.dto.*;
import com.smartmobility.auth.entity.AccountStatus;
import com.smartmobility.auth.entity.AuthCredential;
import com.smartmobility.auth.entity.RefreshToken;
import com.smartmobility.auth.exception.AccountBlockedException;
import com.smartmobility.auth.exception.InvalidCredentialsException;
import com.smartmobility.auth.exception.UserAlreadyExistsException;
import com.smartmobility.auth.mapper.RefreshTokenMapper;
import com.smartmobility.auth.repository.AuthCredentialRepository;
import com.smartmobility.auth.service.AuthService;
import com.smartmobility.auth.entity.OutboxEvent;
import com.smartmobility.auth.repository.OutboxEventRepository;
import com.smartmobility.auth.mapper.AuthMapper;
import com.smartmobility.auth.service.RefreshTokenService;
import com.smartmobility.auth.util.JwtUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final PasswordEncoder passwordEncoder;
    private final AuthCredentialRepository authCredentialRepository;
    private final AuthMapper authMapper;
    private final JwtUtil jwtUtil;
    private final UserServiceClient userServiceClient;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenMapper refreshTokenMapper;
    private final MeterRegistry meterRegistry;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    private <T> T recordDependencyCall(String dependency, String operation, Callable<T> action) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            return action.call();
        } catch (Exception e) {
            outcome = "error";
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        } finally {
            sample.stop(Timer.builder("dependency.client.duration")
                    .description("Duration of downstream service calls")
                    .publishPercentileHistogram()
                    .tag("dependency", dependency)
                    .tag("operation", operation)
                    .tag("outcome", outcome)
                    .register(meterRegistry));
        }
    }



    @Override
    @Transactional
    public AuthResponseDTO register(RegisterRequestDTO request) {
        if (authCredentialRepository.existsByEmail(request.getEmail())){
            throw new UserAlreadyExistsException("Email already registered");
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        AuthCredential credential = authMapper.toEntity(request, hashedPassword);
        credential.setUserId(0L); // Temporary, to satisfy not-null constraint before we get the generated ID
        
        credential = authCredentialRepository.save(credential);
        Long userId = credential.getId();
        credential.setUserId(userId);
        credential = authCredentialRepository.save(credential);

        // 3. Save Outbox Event
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("id", userId);
            payload.put("email", request.getEmail());
            // Add roles if necessary, for now we will just pass a default USER role, 
            // as the request roles are List<String>.
            payload.set("roles", objectMapper.valueToTree(request.getRoles()));
            
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(userId.toString())
                    .eventType("auth.registered")
                    .topic("auth.registered")
                    .payload(payload.toString())
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save auth registered outbox event.", e);
        }

        RefreshToken refreshToken = refreshTokenService.create(userId);

        // 5. Generate JWT
        String token = jwtUtil.generateToken(
                userId,
                credential.getEmail(),
                request.getRoles()
        );

        return authMapper.toDTO(credential, token, refreshToken.getToken());
    }

    @Override
    public AuthResponseDTO login(LoginRequestDTO request) {

        // 1. Fetch user by email
        AuthCredential credential = authCredentialRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

        // 2. Check account status
        if (credential.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountBlockedException("Account is not active");
        }

        // 3. Verify password
        if (!passwordEncoder.matches(request.getPassword(), credential.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        ApiResponse<UserResponseDTO> userResponse = recordDependencyCall(
                "user-service",
                "find-by-email",
                () -> userServiceClient.findByEmail(request.getEmail())
        );


        String accessToken = jwtUtil.generateToken(
                credential.getUserId(),
                credential.getEmail(),
                userResponse.getData().getRoles()
        );

        RefreshToken refreshToken = refreshTokenService.create(credential.getUserId());

        // 4. Return response (JWT later)
        return authMapper.toDTO(credential, accessToken, refreshToken.getToken());
    }

    @Override
    public RefreshResponseDTO refresh(RefreshRequestDTO request) {

        RefreshToken refreshToken = refreshTokenService.validate(request.getRefreshToken());

        AuthCredential credential = authCredentialRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        ApiResponse<UserResponseDTO> userResponse = recordDependencyCall(
                "user-service",
                "find-by-user-id",
                () -> userServiceClient.findByUserId(refreshToken.getUserId())
        );


        String newAccessToken = jwtUtil.generateToken(
                credential.getUserId(),
                credential.getEmail(),
                userResponse.getData().getRoles()
        );

        // 🔥 Token rotation (important)
        refreshTokenService.revoke(request.getRefreshToken());
        RefreshToken newRefreshToken = refreshTokenService.create(credential.getUserId());

        return refreshTokenMapper.toDTO(newAccessToken,newRefreshToken);
    }

    @Override
    public void logout(LogoutRequestDTO request) {
        refreshTokenService.revoke(request.getRefreshToken());
    }

    @Override
    public void logoutAll(Long userId) {
        refreshTokenService.revokeAll(userId);
    }

}
