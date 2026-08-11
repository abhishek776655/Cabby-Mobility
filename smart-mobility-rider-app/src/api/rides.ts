import { apiClient, unwrap } from "./client";
import type { ApiEnvelope, CreateRideRequest, DispatchStatusResponse, Ride } from "./types";

export async function createRide(body: CreateRideRequest): Promise<Ride> {
  const { data } = await apiClient.post<ApiEnvelope<Ride> | Ride>("/rides", body);
  return unwrap(data);
}

export async function getRide(rideId: string): Promise<Ride> {
  const { data } = await apiClient.get<ApiEnvelope<Ride> | Ride>(`/rides/${rideId}`);
  return unwrap(data);
}

export async function cancelRide(rideId: string): Promise<Ride> {
  const { data } = await apiClient.post<ApiEnvelope<Ride> | Ride>(`/rides/${rideId}/cancel`);
  return unwrap(data);
}

export async function retryRide(rideId: string): Promise<Ride> {
  const { data } = await apiClient.post<ApiEnvelope<Ride> | Ride>(`/rides/${rideId}/retry`);
  return unwrap(data);
}

export async function getDispatch(rideId: string): Promise<DispatchStatusResponse> {
  const { data } = await apiClient.get<
    ApiEnvelope<DispatchStatusResponse> | DispatchStatusResponse
  >(`/dispatch/${rideId}`);
  return unwrap(data);
}
