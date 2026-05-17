package com.smartmobility.cab.state.impl;

import com.smartmobility.cab.entity.RideEntity;
import com.smartmobility.cab.exception.InvalidStateTransitionException;
import com.smartmobility.cab.state.RideState;

/**
 * State when no driver was available for matching.
 * 
 * Terminal failure state:
 * - Rider can create a new ride request
 * - System can retry via scheduler
 * - Admin can investigate
 */
public class NoDriverAvailableState implements RideState {

    @Override
    public void match(RideEntity ride) {
        throw new InvalidStateTransitionException("Cannot re-match - create new ride request");
    }

    @Override
    public void assignDriver(RideEntity ride, Long driverId) {
        throw new InvalidStateTransitionException("Cannot assign driver - no driver available");
    }

    @Override
    public void start(RideEntity ride) {
        throw new InvalidStateTransitionException("Cannot start - no driver assigned");
    }

    @Override
    public void complete(RideEntity ride) {
        throw new InvalidStateTransitionException("Cannot complete - ride never started");
    }

    @Override
    public void cancel(RideEntity ride) {
        // Terminal state - no-op
    }

    @Override
    public void failNoDriver(RideEntity ride) {
        // Already in this state - no-op
    }
}