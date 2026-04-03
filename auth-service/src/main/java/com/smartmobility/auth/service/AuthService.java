package com.smartmobility.auth.service;

import com.smartmobility.auth.dto.*;


public interface AuthService {

    AuthResponseDTO register(RegisterRequestDTO request);

    AuthResponseDTO login(LoginRequestDTO request);

    RefreshResponseDTO refresh(RefreshRequestDTO request);

    void logout(LogoutRequestDTO request);

    void logoutAll(Long userId);
}