import type { NavigatorScreenParams } from "@react-navigation/native";
import type { PreferredPaymentMethod, VehicleQuote } from "@/api/types";

/** A fully-resolved trip: both legs, coordinates plus display text. */
export interface TripRoute {
  pickupLat: number;
  pickupLng: number;
  dropLat: number;
  dropLng: number;
  pickupLocation: string;
  dropLocation: string;
}

export type AuthStackParamList = {
  Login: undefined;
  Register: undefined;
};

export interface TripLeg {
  label: string;
  address: string;
  latitude: number;
  longitude: number;
}

export type BookingStackParamList = {
  /**
   * The trip planner and the booking flow's entry point.
   *
   * "plan" mode owns both legs and pushes FareCompare once they're both set. "edit" mode
   * resolves a single leg for a trip that already exists and hands it back through
   * addressPickerStore, so FareCompare can update in place.
   */
  AddressSearch: {
    field: "pickup" | "drop";
    mode?: "plan" | "edit";
    pickup?: TripLeg;
    drop?: TripLeg;
  };
  FareCompare: TripRoute;
  ConfirmBooking: TripRoute & { quote: VehicleQuote; paymentMethod?: PreferredPaymentMethod };
  SearchingDriver: { rideId: string };
  LiveTracking: { rideId: string };
  RideComplete: { rideId: string };
};

export type AppTabsParamList = {
  Home: undefined;
  Wallet: undefined;
  History: undefined;
  Profile: undefined;
};

export type RootStackParamList = {
  Auth: undefined;
  App: undefined;
  Booking: NavigatorScreenParams<BookingStackParamList> | undefined;
};
