import type { VehicleQuote } from "@/api/types";

export interface PickupDropResult {
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

export type BookingStackParamList = {
  PickupDrop: undefined;
  FareCompare: PickupDropResult;
  ConfirmBooking: PickupDropResult & { quote: VehicleQuote };
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
  Booking: { screen?: keyof BookingStackParamList };
};
