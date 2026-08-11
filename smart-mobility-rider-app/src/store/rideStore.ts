import { create } from "zustand";
import { persist, type PersistOptions } from "zustand/middleware";
import AsyncStorage from "@react-native-async-storage/async-storage";
import type { DriverLocationUpdatedEvent, QuoteAllResponse, RideStatus } from "@/api/types";

interface DriverLocation {
  latitude: number;
  longitude: number;
  heading?: number;
  speed?: number;
  timestamp: string;
}

interface RideState {
  activeRideId: string | null;
  rideStatus: RideStatus | null;
  driverLocation: DriverLocation | null;
  fareQuote: QuoteAllResponse | null;
  setActiveRide: (rideId: string, status: RideStatus) => void;
  setRideStatus: (status: RideStatus) => void;
  applyLocationUpdate: (event: DriverLocationUpdatedEvent) => void;
  setFareQuote: (quote: QuoteAllResponse | null) => void;
  clearRide: () => void;
}

type PersistedRideState = Pick<RideState, "activeRideId" | "rideStatus">;

export const useRideStore = create<RideState>()(
  persist<RideState, [], [], PersistedRideState>(
    (set) => ({
      activeRideId: null,
      rideStatus: null,
      driverLocation: null,
      fareQuote: null,
      setActiveRide: (rideId, status) =>
        set({ activeRideId: rideId, rideStatus: status, driverLocation: null }),
      setRideStatus: (status) => set({ rideStatus: status }),
      applyLocationUpdate: (event) =>
        set({
          driverLocation: {
            latitude: event.latitude,
            longitude: event.longitude,
            heading: event.heading,
            speed: event.speed,
            timestamp: event.timestamp,
          },
        }),
      setFareQuote: (quote) => set({ fareQuote: quote }),
      clearRide: () =>
        set({ activeRideId: null, rideStatus: null, driverLocation: null, fareQuote: null }),
    }),
    {
      name: "ride-store",
      storage: {
        getItem: async (key) => {
          const value = await AsyncStorage.getItem(key);
          return value ? JSON.parse(value) : null;
        },
        setItem: async (key, value) => {
          await AsyncStorage.setItem(key, JSON.stringify(value));
        },
        removeItem: async (key) => {
          await AsyncStorage.removeItem(key);
        },
      },
      // Only persist what's needed to resume mid-ride on relaunch — driverLocation and
      // fareQuote are volatile/transient and must not survive a cold start.
      partialize: (state) => ({
        activeRideId: state.activeRideId,
        rideStatus: state.rideStatus,
      }),
    }
  )
);

export function getRideState() {
  return useRideStore.getState();
}
