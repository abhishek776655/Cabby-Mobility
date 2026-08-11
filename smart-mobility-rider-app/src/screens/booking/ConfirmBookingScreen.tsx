import React, { useEffect, useState } from "react";
import { YStack, XStack, Text, Button, Spinner } from "tamagui";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { BookingStackParamList } from "@/navigation/types";
import { createRide } from "@/api/rides";
import { getMe } from "@/api/riders";
import { useAuthStore } from "@/store/authStore";
import { useRideStore } from "@/store/rideStore";
import { toApiError, type ApiError } from "@/api/client";
import type { PreferredPaymentMethod } from "@/api/types";

type Props = NativeStackScreenProps<BookingStackParamList, "ConfirmBooking">;

export function ConfirmBookingScreen({ route, navigation }: Props) {
  const { pickupLat, pickupLng, dropLat, dropLng, pickupLocation, dropLocation, quote } =
    route.params;
  const userId = useAuthStore((s) => s.userId);
  const setActiveRide = useRideStore((s) => s.setActiveRide);

  const [paymentMethod, setPaymentMethod] = useState<PreferredPaymentMethod | null>(null);
  const [booking, setBooking] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(() => {
    getMe()
      .then((profile) => setPaymentMethod(profile.preferredPaymentMethod))
      .catch(() => setPaymentMethod("CASH"));
  }, []);

  const onConfirm = async () => {
    if (!userId) return;
    setBooking(true);
    setError(null);
    try {
      const ride = await createRide({
        riderUserId: userId,
        pickupLocation,
        dropLocation,
        pickupLatitude: pickupLat,
        pickupLongitude: pickupLng,
        dropLatitude: dropLat,
        dropLongitude: dropLng,
        vehicleType: quote.vehicleType,
      });
      setActiveRide(ride.rideId, ride.status);
      navigation.replace("SearchingDriver", { rideId: ride.rideId });
    } catch (e) {
      setError(toApiError(e as any));
    } finally {
      setBooking(false);
    }
  };

  return (
    <YStack flex={1} padding="$4" gap="$4">
      <YStack gap="$1">
        <Text color="$gray10">Pickup</Text>
        <Text fontWeight="600">{pickupLocation}</Text>
      </YStack>
      <YStack gap="$1">
        <Text color="$gray10">Drop</Text>
        <Text fontWeight="600">{dropLocation}</Text>
      </YStack>
      <XStack justifyContent="space-between">
        <Text fontWeight="600">{quote.vehicleType}</Text>
        <Text fontWeight="700" fontSize="$6">
          ₹{(quote.breakdown.total / 100).toFixed(2)}
        </Text>
      </XStack>
      <Text color="$gray10">
        Paying with {paymentMethod ?? "…"}
      </Text>
      {error && <Text color="$red10">{error.message}</Text>}
      <Button theme="active" size="$5" disabled={booking || !paymentMethod} onPress={onConfirm}>
        {booking ? <Spinner /> : "Confirm booking"}
      </Button>
    </YStack>
  );
}
