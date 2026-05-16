package com.smartmobility.auth.service.Impl;

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
import com.smartmobility.auth.mapper.AuthMapper;
import com.smartmobility.auth.service.RefreshTokenService;
import com.smartmobility.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
    @Override
    public AuthResponseDTO register(RegisterRequestDTO request) {
        if (authCredentialRepository.existsByEmail(request.getEmail())){
            throw new UserAlreadyExistsException("Email already registered");
        }
        // 2. Call user-service FIRST (source of truth for user)
        UserCreateRequestDTO userRequest = UserCreateRequestDTO.builder()
                .email(request.getEmail())
                .roles(request.getRoles())
                .build();

        ApiResponse<UserResponseDTO> userResponse = userServiceClient.createUser(userRequest);
        if (userResponse == null || userResponse.getData() == null) {
            throw new RuntimeException("Invalid response from user-service");
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        AuthCredential credential = authMapper.toEntity(request, hashedPassword);

        credential.setUserId(userResponse.getData().getUserId());

        credential = authCredentialRepository.save(credential);
        Long userId = credential.getUserId() != null ? credential.getUserId() : -1L;

        RefreshToken refreshToken = refreshTokenService.create(credential.getUserId());

        // 5. Generate JWT
        String token = jwtUtil.generateToken(
                userId,
                credential.getEmail(),
                userResponse.getData().getRoles()
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

        ApiResponse<UserResponseDTO> userResponse = userServiceClient.findByEmail(request.getEmail());


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

        ApiResponse<UserResponseDTO> userResponse = userServiceClient.findByUserId(refreshToken.getUserId());


        String newAccessToken = jwtUtil.generateToken(
                credential.getUserId(),
                credential.getEmail(),
                userResponse.getData().getRoles()
        );

        // 🔥 Token rotation (important)
        refreshTokenService.revoke(refreshToken.getToken());
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
