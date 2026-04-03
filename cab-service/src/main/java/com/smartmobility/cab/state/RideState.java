package com.smartmobility.cab.state;

import com.smartmobility.cab.entity.RideEntity;

import java.util.UUID;

public interface RideState {

    void match(RideEntity ride);

    void assignDriver(RideEntity ride, UUID driverId);

    void start(RideEntity ride);

    void complete(RideEntity ride);

    void cancel(RideEntity ride);
}
