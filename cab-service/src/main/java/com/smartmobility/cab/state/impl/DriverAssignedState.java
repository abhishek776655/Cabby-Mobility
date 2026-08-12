package com.smartmobility.cab.state.impl;

import com.smartmobility.cab.entity.RideEntity;
import com.smartmobility.cab.entity.RideStatus;
import com.smartmobility.cab.exception.InvalidStateTransitionException;
import com.smartmobility.cab.state.RideState;

public class DriverAssignedState implements RideState {

    public void match(RideEntity ride) {
        throw new InvalidStateTransitionException("Already matched");
    }

    public void assignDriver(RideEntity ride, Long driverUserId) {
        throw new InvalidStateTransitionException("Driver already assigned");
    }

    public void start(RideEntity ride) {
        ride.setStatus(RideStatus.ONGOING);
    }

    public void complete(RideEntity ride) {
        throw new InvalidStateTransitionException("Ride not started");
    }

    // Rider-initiated cancel is still allowed once a driver is assigned but before pickup
    // (ONGOING) — matches the app's LiveTrackingScreen, which offers Cancel through this exact
    // status. RideServiceImpl.cancelRide already publishes RideCancelledEvent with the assigned
    // driverUserId so matchmaking releases their reservation; this state just has to allow the
    // transition instead of rejecting it outright.
    public void cancel(RideEntity ride) {
        ride.setStatus(RideStatus.CANCELLED);
    }

    public void failNoDriver(RideEntity ride) {
        throw new InvalidStateTransitionException("Cannot fail - driver already assigned");
    }

    public void retryMatch(RideEntity ride) {
        throw new InvalidStateTransitionException("Cannot retry - ride not in NO_DRIVER_AVAILABLE state");
    }
}
