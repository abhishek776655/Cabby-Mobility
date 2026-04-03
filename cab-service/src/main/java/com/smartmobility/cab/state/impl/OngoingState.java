package com.smartmobility.cab.state.impl;

import com.smartmobility.cab.entity.RideEntity;
import com.smartmobility.cab.entity.RideStatus;
import com.smartmobility.cab.exception.InvalidStateTransitionException;
import com.smartmobility.cab.state.RideState;

import java.util.UUID;

public class OngoingState implements RideState {

    public void match(RideEntity ride) {
        throw new InvalidStateTransitionException("Ride already started");
    }

    public void assignDriver(RideEntity ride, UUID driverId) {
        throw new InvalidStateTransitionException("Driver already assigned");
    }

    public void start(RideEntity ride) {
        throw new InvalidStateTransitionException("Already started");
    }

    public void complete(RideEntity ride) {
        ride.setStatus(RideStatus.COMPLETED);
    }

    public void cancel(RideEntity ride) {
        throw new InvalidStateTransitionException("Cannot cancel ongoing ride");
    }
}
