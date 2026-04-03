package com.smartmobility.auth.controller;

import com.smartmobility.auth.dto.*;
import com.smartmobility.auth.service.AuthService;
import com.smartmobility.auth.util.ApiResponseBuilder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 🔹 Register
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> register(
            @Valid @RequestBody RegisterRequestDTO request) {

        AuthResponseDTO response = authService.register(request);

        return ResponseEntity.ok(
                ApiResponseBuilder.success(response, "User registered successfully", HttpStatus.CREATED)
        );
    }

    // 🔹 Login
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> login(
            @Valid @RequestBody LoginRequestDTO request) {

        AuthResponseDTO response = authService.login(request);

        return ResponseEntity.ok(
                ApiResponseBuilder.success(response, "Login successful", HttpStatus.OK)
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponseDTO>> refresh(
            @RequestBody RefreshRequestDTO request) {

        RefreshResponseDTO response = authService.refresh(request);

        return ResponseEntity.ok(
                ApiResponseBuilder.success(response, "Token refreshed", HttpStatus.OK)
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody LogoutRequestDTO request) {

        authService.logout(request);

        return ResponseEntity.ok(
                ApiResponseBuilder.success(null, "Logged out successfully", HttpStatus.OK)
        );
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll() {

        Long userId = (Long) Objects.requireNonNull(SecurityContextHolder
                        .getContext()
                        .getAuthentication())
                .getPrincipal();

        authService.logoutAll(userId);

        return ResponseEntity.ok(
                ApiResponseBuilder.success(null, "Logged out from all devices", HttpStatus.OK)
        );
    }
}
