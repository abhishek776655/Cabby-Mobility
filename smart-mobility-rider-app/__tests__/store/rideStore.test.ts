import { useRideStore } from "@/store/rideStore";
import type { DriverLocationUpdatedEvent } from "@/api/types";

describe("rideStore", () => {
  beforeEach(() => {
    useRideStore.setState({
      activeRideId: null,
      rideStatus: null,
      driverLocation: null,
      fareQuote: null,
    });
  });

  it("setActiveRide sets rideId/status and clears any stale driver location", () => {
    useRideStore.setState({ driverLocation: { latitude: 1, longitude: 1, timestamp: "t" } });

    useRideStore.getState().setActiveRide("ride-1", "REQUESTED");

    const state = useRideStore.getState();
    expect(state.activeRideId).toBe("ride-1");
    expect(state.rideStatus).toBe("REQUESTED");
    expect(state.driverLocation).toBeNull();
  });

  it("applyLocationUpdate maps the event fields onto driverLocation", () => {
    const event: DriverLocationUpdatedEvent = {
      driverUserId: 10,
      rideId: "ride-1",
      latitude: 12.97,
      longitude: 77.59,
      speed: 5.5,
      heading: 90,
      timestamp: "2026-08-11T10:00:00Z",
    };

    useRideStore.getState().applyLocationUpdate(event);

    expect(useRideStore.getState().driverLocation).toEqual({
      latitude: 12.97,
      longitude: 77.59,
      speed: 5.5,
      heading: 90,
      timestamp: "2026-08-11T10:00:00Z",
    });
  });

  it("clearRide resets everything including the fare quote", () => {
    useRideStore.setState({
      activeRideId: "ride-1",
      rideStatus: "ONGOING",
      driverLocation: { latitude: 1, longitude: 1, timestamp: "t" },
      fareQuote: {} as any,
    });

    useRideStore.getState().clearRide();

    const state = useRideStore.getState();
    expect(state.activeRideId).toBeNull();
    expect(state.rideStatus).toBeNull();
    expect(state.driverLocation).toBeNull();
    expect(state.fareQuote).toBeNull();
  });
});
