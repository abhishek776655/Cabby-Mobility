package com.smartmobility.auth.dto;

import com.smartmobility.auth.entity.Role;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDTO {

    private Long userId;
    private String email;
    private Role role;
}
