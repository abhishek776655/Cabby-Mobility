package com.smartmobility.driver_service.event;

import lombok.Data;

import java.util.Set;

@Data
public class UserCreatedEvent {

    private Long userId;
    private String email;
    private Set<String> roles;
}