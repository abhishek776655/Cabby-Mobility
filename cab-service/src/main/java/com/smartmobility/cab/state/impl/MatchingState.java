package com.smartmobility.cab.state.impl;

import com.smartmobility.cab.entity.RideEntity;
import com.smartmobility.cab.entity.RideStatus;
import com.smartmobility.cab.exception.InvalidStateTransitionException;
import com.smartmobility.cab.state.RideState;

import java.util.UUID;

public class MatchingState implements RideState {
    public void match(RideEntity ride) {
        throw new InvalidStateTransitionException("Already matching");
    }

    public void assignDriver(RideEntity ride, UUID driverId) {
        ride.setDriverId(driverId);
        ride.setStatus(RideStatus.DRIVER_ASSIGNED);
    }

    public void start(RideEntity ride) {
        throw new InvalidStateTransitionException("Driver not assigned yet");
    }

    public void complete(RideEntity ride) {
        throw new InvalidStateTransitionException("Ride not started");
    }

    public void cancel(RideEntity ride) {
        ride.setStatus(RideStatus.CANCELLED);
    }
}
