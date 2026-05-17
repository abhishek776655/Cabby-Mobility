package com.smartmobility.user.event;

import lombok.*;

import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserCreatedEvent {

    private Long userId;
    private String email;
    private Set<String> roles;
}
