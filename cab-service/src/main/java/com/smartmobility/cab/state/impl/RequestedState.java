package com.smartmobility.cab.state.impl;

import com.smartmobility.cab.entity.RideEntity;
import com.smartmobility.cab.entity.RideStatus;
import com.smartmobility.cab.exception.InvalidStateTransitionException;
import com.smartmobility.cab.state.RideState;

public class RequestedState implements RideState {
    public void match(RideEntity ride) {
        ride.setStatus(RideStatus.MATCHING);
    }

    public void assignDriver(RideEntity ride, Long driverUserId) {
        throw new InvalidStateTransitionException("Cannot assign driver in REQUESTED state");
    }

    public void start(RideEntity ride) {
        throw new InvalidStateTransitionException("Cannot start ride in REQUESTED state");
    }

    public void complete(RideEntity ride) {
        throw new InvalidStateTransitionException("Cannot complete ride in REQUESTED state");
    }

    public void cancel(RideEntity ride) {
        ride.setStatus(RideStatus.CANCELLED);
    }

    public void failNoDriver(RideEntity ride) {
        throw new InvalidStateTransitionException("Cannot fail in REQUESTED state - must match first");
    }

}
