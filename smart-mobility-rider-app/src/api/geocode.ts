import { apiClient, unwrap } from "./client";
import type { ApiEnvelope, GeocodeSuggestion } from "./types";

export interface AutocompleteRequest {
  query: string;
  /** Bias results toward the rider's current position when known. */
  lat?: number;
  lon?: number;
  limit?: number;
  signal?: AbortSignal;
}

/**
 * Address autocomplete. The serviceable-area bbox is applied by routing-service, not here —
 * the client cannot widen it, so every suggestion returned is guaranteed routable.
 */
export async function autocompleteAddress({
  query,
  lat,
  lon,
  limit,
  signal,
}: AutocompleteRequest): Promise<GeocodeSuggestion[]> {
  const { data } = await apiClient.get<ApiEnvelope<GeocodeSuggestion[]> | GeocodeSuggestion[]>(
    "/geocode/autocomplete",
    { params: { q: query, lat, lon, limit }, signal }
  );
  return unwrap(data);
}

/**
 * Names the place under a dropped map pin. Resolves to null when the backend has no match or
 * the point is outside the serviceable area — it answers 204 for both, which is an ordinary
 * outcome ("keep moving the pin"), not an error.
 */
export async function reverseGeocode(
  lat: number,
  lon: number,
  signal?: AbortSignal
): Promise<GeocodeSuggestion | null> {
  const response = await apiClient.get<ApiEnvelope<GeocodeSuggestion> | GeocodeSuggestion | "">(
    "/geocode/reverse",
    { params: { lat, lon }, signal }
  );
  if (response.status === 204 || !response.data) return null;
  return unwrap(response.data as ApiEnvelope<GeocodeSuggestion> | GeocodeSuggestion);
}
