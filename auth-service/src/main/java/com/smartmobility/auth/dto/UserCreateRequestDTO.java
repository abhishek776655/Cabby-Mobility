package com.smartmobility.auth.dto;

import com.smartmobility.auth.entity.Role;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCreateRequestDTO {

    private String email;
    private Set<Role> roles;
}
