package com.smartmobility.auth.dto;

import com.smartmobility.auth.entity.Role;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthResponseDTO {

    private String accessToken;
    private Long userId;
    private Role role;
    private String refreshToken;

}