package com.smartmobility.cab.state;

import com.smartmobility.cab.entity.RideEntity;

public interface RideState {

    void match(RideEntity ride);

    void assignDriver(RideEntity ride, Long driverId);

    void start(RideEntity ride);

    void complete(RideEntity ride);

    void cancel(RideEntity ride);

    void failNoDriver(RideEntity ride);
}
