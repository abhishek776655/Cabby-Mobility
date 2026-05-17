package com.smartmobility.user.dto;

import com.smartmobility.user.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class CreateUserDTO {

    @Email
    private String email;

    @NotNull
    private Set<Role> roles;
}