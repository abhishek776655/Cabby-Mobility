package com.smartmobility.cab.security;

import com.smartmobility.cab.entity.RideEntity;
import com.smartmobility.cab.exception.ForbiddenAccessException;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class RideAuthorizationGuard {

    public void assertRiderOwnsRide(RideEntity ride, Long currentUserId) {
        if (!Objects.equals(ride.getRiderUserId(), currentUserId)) {
            throw new ForbiddenAccessException("You are not allowed to access this ride");
        }
    }

    public void assertAssignedDriverOwnsRide(RideEntity ride, Long currentUserId) {
        if (!Objects.equals(ride.getDriverUserId(), currentUserId)) {
            throw new ForbiddenAccessException("You are not allowed to access this ride");
        }
    }

    public void assertRideOwnedByRiderOrDriver(RideEntity ride, Long currentUserId) {
        if (Objects.equals(ride.getRiderUserId(), currentUserId)) {
            return;
        }

        if (Objects.equals(ride.getDriverUserId(), currentUserId)) {
            return;
        }

        throw new ForbiddenAccessException("You are not allowed to access this ride");
    }

    public void assertCurrentDriverMatchesRequest(Long requestDriverUserId, Long currentUserId) {
        if (!Objects.equals(requestDriverUserId, currentUserId)) {
            throw new ForbiddenAccessException("You are not allowed to respond for another driver");
        }
    }

    public void assertAdminRole(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            throw new ForbiddenAccessException("Admin access required");
        }

        for (String role : rolesHeader.split(",")) {
            if ("ADMIN".equals(role.trim())) {
                return;
            }
        }

        throw new ForbiddenAccessException("Admin access required");
    }
}
