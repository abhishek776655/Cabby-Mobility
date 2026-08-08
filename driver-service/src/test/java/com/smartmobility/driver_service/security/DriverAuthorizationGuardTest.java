package com.smartmobility.driver_service.security;

import com.smartmobility.driver_service.exception.ForbiddenAccessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DriverAuthorizationGuardTest {

    private final DriverAuthorizationGuard guard = new DriverAuthorizationGuard();

    @Test
    void allowsSelfAccess() {
        assertDoesNotThrow(() -> guard.assertSelfOrAdmin(42L, 42L, "DRIVER"));
    }

    @Test
    void allowsAdminAccess() {
        assertDoesNotThrow(() -> guard.assertSelfOrAdmin(42L, 7L, "ADMIN,DRIVER"));
    }

    @Test
    void rejectsDifferentDriverWithoutAdmin() {
        assertThrows(
                ForbiddenAccessException.class,
                () -> guard.assertSelfOrAdmin(42L, 7L, "DRIVER")
        );
    }
}
