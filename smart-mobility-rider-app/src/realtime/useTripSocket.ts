import { useEffect, useState } from "react";
import { tripStompClient } from "./stompClient";
import { useRideStore } from "@/store/rideStore";

/**
 * Subscribes to /topic/trip/{rideId} for live driver-location updates only — this topic never
 * carries ride-status changes (confirmed against realtime-gateway-service's broadcast wiring).
 * Status must still come from polling GET /rides/{rideId} (see useRidePolling).
 */
export function useTripSocket(rideId: string | null): { connected: boolean; error: string | null } {
  const applyLocationUpdate = useRideStore((s) => s.applyLocationUpdate);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!rideId) {
      tripStompClient.disconnect();
      setConnected(false);
      setError(null);
      return;
    }

    tripStompClient.connect(rideId, applyLocationUpdate);

    const interval = setInterval(() => {
      setConnected(tripStompClient.isConnected());
      setError(tripStompClient.getLastError());
    }, 2000);

    return () => {
      clearInterval(interval);
      tripStompClient.disconnect();
    };
  }, [rideId, applyLocationUpdate]);

  return { connected, error };
}
