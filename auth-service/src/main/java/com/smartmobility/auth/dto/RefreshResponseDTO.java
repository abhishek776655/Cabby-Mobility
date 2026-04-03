package com.smartmobility.auth.dto;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RefreshResponseDTO {
    private String accessToken;
    private String refreshToken;
}
