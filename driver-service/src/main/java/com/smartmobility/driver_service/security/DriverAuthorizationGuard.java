package com.smartmobility.driver_service.security;

import com.smartmobility.driver_service.exception.ForbiddenAccessException;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class DriverAuthorizationGuard {

    public void assertSelfOrAdmin(Long targetUserId, Long currentUserId, String rolesHeader) {
        if (isAdmin(rolesHeader)) {
            return;
        }

        if (!Objects.equals(targetUserId, currentUserId)) {
            throw new ForbiddenAccessException("You are not allowed to access this driver resource");
        }
    }

    private boolean isAdmin(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return false;
        }

        for (String role : rolesHeader.split(",")) {
            if ("ADMIN".equals(role.trim())) {
                return true;
            }
        }

        return false;
    }
}
