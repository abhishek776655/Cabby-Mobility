package com.smartmobility.auth.mapper;


import com.smartmobility.auth.dto.RefreshResponseDTO;
import com.smartmobility.auth.entity.RefreshToken;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenMapper {

    // 🔹 Entity → Response DTO
    public RefreshResponseDTO toDTO(String accessToken, RefreshToken refreshToken) {
        return RefreshResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .build();
    }

    // 🔹 Optional: only if needed (rare)
    public String extractToken(RefreshToken refreshToken) {
        return refreshToken.getToken();
    }
}