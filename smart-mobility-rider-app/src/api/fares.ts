import { apiClient, unwrap } from "./client";
import type { ApiEnvelope, QuoteAllResponse } from "./types";

export interface QuoteAllRequest {
  pickupLat: number;
  pickupLng: number;
  dropLat: number;
  dropLng: number;
}

export async function quoteAll(body: QuoteAllRequest): Promise<QuoteAllResponse> {
  const { data } = await apiClient.post<ApiEnvelope<QuoteAllResponse> | QuoteAllResponse>(
    "/fares/quote-all",
    body
  );
  return unwrap(data);
}
