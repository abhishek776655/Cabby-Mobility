package com.smartmobility.cab.entity;

public enum RideStatus {
    REQUESTED,
    MATCHING,
    DRIVER_ASSIGNED,
    ONGOING,
    COMPLETED,
    CANCELLED,
    NO_DRIVER_AVAILABLE  // Added for matchmaking failure
}
