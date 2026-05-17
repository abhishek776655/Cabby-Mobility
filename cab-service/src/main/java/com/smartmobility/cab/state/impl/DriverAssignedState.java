package com.smartmobility.cab.state.impl;

import com.smartmobility.cab.entity.RideEntity;
import com.smartmobility.cab.entity.RideStatus;
import com.smartmobility.cab.exception.InvalidStateTransitionException;
import com.smartmobility.cab.state.RideState;

public class DriverAssignedState implements RideState {

    public void match(RideEntity ride) {
        throw new InvalidStateTransitionException("Already matched");
    }

    public void assignDriver(RideEntity ride, Long driverId) {
        throw new InvalidStateTransitionException("Driver already assigned");
    }

    public void start(RideEntity ride) {
        ride.setStatus(RideStatus.ONGOING);
    }

    public void complete(RideEntity ride) {
        throw new InvalidStateTransitionException("Ride not started");
    }

public void cancel(RideEntity ride) {
        throw new InvalidStateTransitionException("Cannot cancel - driver already assigned");
    }

    public void failNoDriver(RideEntity ride) {
        throw new InvalidStateTransitionException("Cannot fail - driver already assigned");
    }
}
