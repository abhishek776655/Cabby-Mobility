import { apiClient, unwrap } from "./client";
import type { ApiEnvelope, PreferredPaymentMethod, RiderProfile, SavedLocation } from "./types";

export async function getMe(): Promise<RiderProfile> {
  const { data } = await apiClient.get<ApiEnvelope<RiderProfile> | RiderProfile>("/riders/me");
  return unwrap(data);
}

export async function updatePreferences(
  preferredPaymentMethod: PreferredPaymentMethod
): Promise<RiderProfile> {
  const { data } = await apiClient.patch<ApiEnvelope<RiderProfile> | RiderProfile>(
    "/riders/me/preferences",
    { preferredPaymentMethod }
  );
  return unwrap(data);
}

export async function getLocations(): Promise<SavedLocation[]> {
  const { data } = await apiClient.get<ApiEnvelope<SavedLocation[]> | SavedLocation[]>(
    "/riders/me/locations"
  );
  return unwrap(data);
}

export async function addLocation(
  location: Omit<SavedLocation, "id">
): Promise<SavedLocation> {
  const { data } = await apiClient.post<ApiEnvelope<SavedLocation> | SavedLocation>(
    "/riders/me/locations",
    location
  );
  return unwrap(data);
}

export async function deleteLocation(locationId: number): Promise<void> {
  await apiClient.delete(`/riders/me/locations/${locationId}`);
}
