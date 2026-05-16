package com.smartmobility.user_service.dto;

import com.smartmobility.user_service.entity.Role;
import lombok.*;

import java.util.Set;

@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {

    private Long userId;
    private String email;
    private Set<Role> roles;
}
