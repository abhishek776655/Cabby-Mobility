package com.smartmobility.user_service.dto;

import com.smartmobility.user_service.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserDTO {

    @Email
    private String email;

    @NotNull
    private Role role;
}