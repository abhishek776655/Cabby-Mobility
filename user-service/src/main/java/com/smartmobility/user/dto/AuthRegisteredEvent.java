package com.smartmobility.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRegisteredEvent {
    private Long id;
    private String email;
    private Set<String> roles;
}
