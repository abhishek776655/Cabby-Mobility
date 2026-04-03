package com.smartmobility.cab.state.impl;

import com.smartmobility.cab.entity.RideEntity;
import com.smartmobility.cab.entity.RideStatus;
import com.smartmobility.cab.exception.InvalidStateTransitionException;
import com.smartmobility.cab.state.RideState;

import java.util.UUID;

public class RequestedState implements RideState {
    public void match(RideEntity ride) {
        ride.setStatus(RideStatus.MATCHING);
    }

    public void assignDriver(RideEntity ride, UUID driverId) {
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

}
