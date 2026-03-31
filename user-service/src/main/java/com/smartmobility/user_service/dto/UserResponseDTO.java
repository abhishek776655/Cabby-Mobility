package com.smartmobility.user_service.dto;

import com.smartmobility.user_service.entity.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponseDTO {

    private Long userId;
    private String email;
    private Role role;
}