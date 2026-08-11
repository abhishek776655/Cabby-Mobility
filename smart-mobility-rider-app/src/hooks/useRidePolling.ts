import { useEffect, useRef, useState } from "react";
import { getRide } from "@/api/rides";
import { useRideStore } from "@/store/rideStore";
import { RIDE_POLL_INTERVAL_MS } from "@/constants/config";
import type { Ride } from "@/api/types";
import { toApiError, type ApiError } from "@/api/client";
import type { AxiosError } from "axios";

/**
 * Polls GET /rides/{rideId} on an interval. This is the ONLY channel for ride status
 * transitions today — the STOMP socket carries driver-location updates exclusively, so this
 * hook is not a fallback, it's the authoritative source while a ride is active.
 */
export function useRidePolling(rideId: string | null): {
  ride: Ride | null;
  error: ApiError | null;
} {
  const setRideStatus = useRideStore((s) => s.setRideStatus);
  const [ride, setRide] = useState<Ride | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!rideId) {
      setRide(null);
      return;
    }

    let cancelled = false;

    const poll = async () => {
      try {
        const latest = await getRide(rideId);
        if (cancelled) return;
        setRide(latest);
        setRideStatus(latest.status);
        setError(null);
      } catch (e) {
        if (cancelled) return;
        setError(toApiError(e as AxiosError));
      }
    };

    poll();
    timerRef.current = setInterval(poll, RIDE_POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [rideId, setRideStatus]);

  return { ride, error };
}
