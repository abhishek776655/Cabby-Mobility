package com.smartmobility.auth.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogoutRequestDTO {
    private String refreshToken;
}
