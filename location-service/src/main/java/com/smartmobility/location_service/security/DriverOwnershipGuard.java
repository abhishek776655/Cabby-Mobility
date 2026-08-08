package com.smartmobility.location_service.security;

import com.smartmobility.location_service.exception.ForbiddenAccessException;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class DriverOwnershipGuard {

    public void assertSelf(Long targetDriverUserId, Long currentUserId) {
        if (!Objects.equals(targetDriverUserId, currentUserId)) {
            throw new ForbiddenAccessException("You are not allowed to modify another driver's location");
        }
    }
}
