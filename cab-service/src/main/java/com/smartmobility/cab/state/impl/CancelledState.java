package com.smartmobility.cab.state.impl;

import com.smartmobility.cab.entity.RideEntity;
import com.smartmobility.cab.exception.InvalidStateTransitionException;
import com.smartmobility.cab.state.RideState;

import java.util.UUID;

public class CancelledState implements RideState {

    public void match(RideEntity ride) { throw error(); }
    public void assignDriver(RideEntity ride, UUID driverId) { throw error(); }
    public void start(RideEntity ride) { throw error(); }
    public void complete(RideEntity ride) { throw error(); }
    public void cancel(RideEntity ride) { throw error(); }

    private RuntimeException error() {
        return new InvalidStateTransitionException("Ride already cancelled");
    }
}