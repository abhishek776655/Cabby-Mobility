package com.smartmobility.auth.mapper;

import com.smartmobility.auth.dto.AuthResponseDTO;
import com.smartmobility.auth.dto.RegisterRequestDTO;
import com.smartmobility.auth.entity.AuthCredential;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public AuthCredential toEntity(RegisterRequestDTO request, String hashedPassword) {
        return AuthCredential.builder()
                .email(request.getEmail())
                .passwordHash(hashedPassword)
                .role(request.getRole())
                .build();
    }

    public AuthResponseDTO toDTO(AuthCredential credential, String token, String refreshToken) {
        return AuthResponseDTO.builder()
                .accessToken(token)
                .refreshToken(refreshToken)
                .userId(credential.getUserId())
                .role(credential.getRole())
                .build();
    }
}