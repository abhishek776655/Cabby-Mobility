import React, { useEffect, useState } from "react";
import { YStack, Text, Button, Spinner } from "tamagui";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { BookingStackParamList } from "@/navigation/types";
import { getRide } from "@/api/rides";
import { useRideStore } from "@/store/rideStore";
import type { Ride } from "@/api/types";

type Props = NativeStackScreenProps<BookingStackParamList, "RideComplete">;

export function RideCompleteScreen({ route, navigation }: Props) {
  const { rideId } = route.params;
  const clearRide = useRideStore((s) => s.clearRide);
  const [ride, setRide] = useState<Ride | null>(null);

  useEffect(() => {
    getRide(rideId).then(setRide);
    clearRide();
  }, [rideId, clearRide]);

  if (!ride) {
    return (
      <YStack flex={1} justifyContent="center" alignItems="center">
        <Spinner size="large" />
      </YStack>
    );
  }

  return (
    <YStack flex={1} padding="$4" justifyContent="center" gap="$4" alignItems="center">
      <Text fontSize="$8" fontWeight="700">
        Trip complete
      </Text>
      <Text fontSize="$10" fontWeight="800">
        ₹{ride.fare != null ? (ride.fare / 100).toFixed(2) : "—"}
      </Text>
      <Text color="$gray10">Already deducted from your wallet</Text>
      <Text color="$gray10" textAlign="center">
        {ride.pickupLocation} → {ride.dropLocation}
      </Text>
      <Button theme="active" onPress={() => navigation.getParent()?.goBack()}>
        Done
      </Button>
    </YStack>
  );
}
