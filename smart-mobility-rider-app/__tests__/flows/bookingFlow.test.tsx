import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react-native";
import { TamaguiProvider, Theme } from "tamagui";
import tamaguiConfig from "../../tamagui.config";
import { FareCompareScreen } from "@/screens/booking/FareCompareScreen";
import { ConfirmBookingScreen } from "@/screens/booking/ConfirmBookingScreen";
import * as faresApi from "@/api/fares";
import * as ridesApi from "@/api/rides";
import * as ridersApi from "@/api/riders";
import { useAuthStore } from "@/store/authStore";
import { useRideStore } from "@/store/rideStore";

jest.mock("@/api/fares");
jest.mock("@/api/rides");
jest.mock("@/api/riders");
jest.mock("expo-location", () => ({
  requestForegroundPermissionsAsync: jest.fn().mockResolvedValue({ status: "denied" }),
  getCurrentPositionAsync: jest.fn().mockResolvedValue({ coords: { latitude: 0, longitude: 0 } }),
}));

function renderWithProvider(children: React.ReactElement) {
  return render(
    <TamaguiProvider config={tamaguiConfig} defaultTheme="light">
      <Theme name="light">{children}</Theme>
    </TamaguiProvider>
  );
}

const baseRouteParams = {
  pickupLat: 12.97,
  pickupLng: 77.59,
  dropLat: 12.93,
  dropLng: 77.62,
  pickupLocation: "Home",
  dropLocation: "Office",
};

describe("booking flow: FareCompare -> ConfirmBooking -> POST /rides", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useAuthStore.setState({ userId: 7, status: "authenticated" });
    useRideStore.setState({ activeRideId: null, rideStatus: null, fareQuote: null });
    (ridersApi.getMe as jest.Mock).mockResolvedValue({
      id: 1,
      userId: 7,
      rating: 4.8,
      preferredPaymentMethod: "CASH",
    });
    (ridersApi.updatePreferences as jest.Mock).mockResolvedValue({
      id: 1,
      userId: 7,
      rating: 4.8,
      preferredPaymentMethod: "CASH",
    });
    (ridersApi.getLocations as jest.Mock).mockResolvedValue([]);
  });

  it("selecting a fare on FareCompare navigates to ConfirmBooking with that quote", async () => {
    (faresApi.quoteAll as jest.Mock).mockResolvedValue({
      polyline: "abc",
      coordinates: [],
      distanceMeters: 5000,
      durationSeconds: 600,
      estimateSource: "VALHALLA",
      currency: "INR",
      quotes: [
        { vehicleType: "STANDARD", breakdown: { baseFare: 5000, distanceFare: 0, timeFare: 0, surgeAmount: 0, total: 9000, surgeMultiplier: 1 } },
        { vehicleType: "PREMIUM", breakdown: { baseFare: 8000, distanceFare: 0, timeFare: 0, surgeAmount: 0, total: 15000, surgeMultiplier: 1 } },
      ],
    });

    const navigate = jest.fn();
    renderWithProvider(
      <FareCompareScreen
        route={{ params: baseRouteParams } as any}
        navigation={{ navigate } as any}
      />
    );

    await waitFor(() => expect(screen.getByText("Premium")).toBeTruthy());
    fireEvent.press(screen.getByText("Premium"));
    await waitFor(() => expect(screen.getByText(/Book Premium/)).toBeTruthy());
    fireEvent.press(screen.getByText(/Book Premium/));

    expect(navigate).toHaveBeenCalledWith(
      "ConfirmBooking",
      expect.objectContaining({
        ...baseRouteParams,
        quote: expect.objectContaining({ vehicleType: "PREMIUM" }),
      })
    );
  });

  it("confirming the booking calls POST /rides with the selected vehicleType and sets the active ride", async () => {
    (ridersApi.getMe as jest.Mock).mockResolvedValue({
      id: 1,
      userId: 7,
      rating: 4.8,
      preferredPaymentMethod: "CASH",
    });
    (ridesApi.createRide as jest.Mock).mockResolvedValue({
      rideId: "ride-123",
      riderUserId: 7,
      driverUserId: null,
      pickupLocation: "Home",
      dropLocation: "Office",
      pickupLatitude: 12.97,
      pickupLongitude: 77.59,
      dropLatitude: 12.93,
      dropLongitude: 77.62,
      vehicleType: "PREMIUM",
      status: "REQUESTED",
      fare: null,
      createdAt: "now",
      updatedAt: "now",
    });

    const replace = jest.fn();
    renderWithProvider(
      <ConfirmBookingScreen
        route={{
          params: {
            ...baseRouteParams,
            quote: {
              vehicleType: "PREMIUM",
              breakdown: { baseFare: 8000, distanceFare: 0, timeFare: 0, surgeAmount: 0, total: 15000, surgeMultiplier: 1 },
            },
          },
        } as any}
        navigation={{ replace } as any}
      />
    );

    await waitFor(() => expect(screen.getByText(/Paying with Cash/)).toBeTruthy());
    fireEvent.press(screen.getByText(/Confirm/));

    await waitFor(() =>
      expect(ridesApi.createRide).toHaveBeenCalledWith(
        expect.objectContaining({ riderUserId: 7, vehicleType: "PREMIUM" })
      )
    );
    expect(useRideStore.getState().activeRideId).toBe("ride-123");
    expect(replace).toHaveBeenCalledWith("SearchingDriver", { rideId: "ride-123" });
  });
});
