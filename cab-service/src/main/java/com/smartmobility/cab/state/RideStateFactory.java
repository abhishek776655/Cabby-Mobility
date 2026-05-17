package com.smartmobility.cab.state;

import com.smartmobility.cab.entity.RideStatus;
import com.smartmobility.cab.state.impl.*;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class RideStateFactory {

    private final Map<RideStatus, RideState> stateMap = new EnumMap<>(RideStatus.class);

    public RideStateFactory() {
        stateMap.put(RideStatus.REQUESTED, new RequestedState());
        stateMap.put(RideStatus.MATCHING, new MatchingState());
        stateMap.put(RideStatus.DRIVER_ASSIGNED, new DriverAssignedState());
        stateMap.put(RideStatus.ONGOING, new OngoingState());
        stateMap.put(RideStatus.COMPLETED, new CompletedState());
        stateMap.put(RideStatus.CANCELLED, new CancelledState());
        stateMap.put(RideStatus.NO_DRIVER_AVAILABLE, new NoDriverAvailableState());
    }

    public RideState getState(RideStatus status) {
        RideState state = stateMap.get(status);

        if (state == null) {
            throw new IllegalArgumentException("No state found for status: " + status);
        }

        return state;
    }
}