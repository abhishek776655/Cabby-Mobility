// Mirrors backend DTOs exactly — see docs/superpowers/plans for the source-of-truth field
// confirmations (auth-service, rider-service, cab-service, pricing-service, payment-service,
// realtime-gateway-service).

export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  message?: string;
}

// --- auth-service ---

export type Role = "RIDER" | "DRIVER" | "ADMIN";

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  userId: number;
  role: Role;
}

// --- rider-service ---

export type PreferredPaymentMethod = "CASH" | "CARD" | "WALLET";

export interface RiderProfile {
  id: number;
  userId: number;
  rating: number | null;
  preferredPaymentMethod: PreferredPaymentMethod;
}

export interface SavedLocation {
  id: number;
  label: string;
  address: string;
  latitude: number;
  longitude: number;
}

// --- pricing-service ---

export interface FareBreakdown {
  baseFare: number;
  distanceFare: number;
  timeFare: number;
  surgeAmount: number;
  total: number;
  surgeMultiplier: number;
}

export interface VehicleQuote {
  vehicleType: string;
  breakdown: FareBreakdown;
}

export interface Coordinate {
  lat: number;
  lng: number;
}

export interface QuoteAllResponse {
  polyline: string;
  coordinates: Coordinate[];
  distanceMeters: number;
  durationSeconds: number;
  estimateSource: string;
  currency: string;
  quotes: VehicleQuote[];
}

// --- routing-service (address autocomplete) ---

export interface GeocodeSuggestion {
  /** Primary line, e.g. "Qutub Minar Complex". */
  label: string;
  /** Secondary line, e.g. "Baba Shrichand Marg, South Delhi"; may be empty. */
  description: string;
  lat: number;
  lng: number;
  /** Raw OSM value ("monument", "suburb", "station", ...) for choosing a row icon. */
  kind: string | null;
}

// --- cab-service ---

// Cab-service's own ride status (RideEntity.RideStatus) — the AUTHORITATIVE status,
// distinct from matchmaking's internal dispatch status below.
export type RideStatus =
  | "REQUESTED"
  | "MATCHING"
  | "DRIVER_ASSIGNED"
  | "ONGOING"
  | "COMPLETED"
  | "CANCELLED"
  | "NO_DRIVER_AVAILABLE";

export interface Ride {
  rideId: string;
  riderUserId: number;
  driverUserId: number | null;
  pickupLocation: string;
  dropLocation: string;
  pickupLatitude: number;
  pickupLongitude: number;
  dropLatitude: number;
  dropLongitude: number;
  vehicleType: string;
  status: RideStatus;
  fare: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRideRequest {
  riderUserId: number;
  pickupLocation: string;
  dropLocation: string;
  pickupLatitude: number;
  pickupLongitude: number;
  dropLatitude: number;
  dropLongitude: number;
  vehicleType: string;
}

// matchmaking-service's internal dispatch session status, exposed via GET /dispatch/{rideId} —
// richer/more granular than Ride.status, useful for the "searching" animation, but never the
// value that gates navigation (that's always Ride.status).
export type DispatchStatus =
  | "SEARCHING"
  | "ASSIGNMENT_SENT"
  | "RETRYING"
  | "WIDENING_SEARCH"
  | "ASSIGNED"
  | "FAILED"
  | "CANCELLED";

export interface DispatchStatusResponse {
  dispatchId: string;
  rideId: string;
  status: DispatchStatus;
  driverUserId: number | null;
  retryCount: number;
  createdAt: string;
  expiresAt: string;
}

// --- payment-service ---

export interface WalletBalance {
  userId: number;
  balance: number;
}

export type WalletTransactionType = "DEBIT" | "CREDIT" | "TOPUP";

export interface WalletTransaction {
  id: string;
  userId: number;
  rideId: string | null;
  eventId: string;
  type: WalletTransactionType;
  amount: number;
  balanceAfter: number;
  status: string;
  createdAt: string;
}

// --- realtime-gateway-service (STOMP /topic/trip/{rideId} message body) ---

export interface DriverLocationUpdatedEvent {
  driverUserId: number;
  rideId: string;
  latitude: number;
  longitude: number;
  speed?: number;
  heading?: number;
  timestamp: string;
}
