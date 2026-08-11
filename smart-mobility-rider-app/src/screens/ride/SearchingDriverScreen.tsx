import React from "react";
import { YStack, Text, Button, Spinner } from "tamagui";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { BookingStackParamList } from "@/navigation/types";
import { useRidePolling } from "@/hooks/useRidePolling";
import { retryRide, cancelRide } from "@/api/rides";
import { useRideStore } from "@/store/rideStore";

type Props = NativeStackScreenProps<BookingStackParamList, "SearchingDriver">;

export function SearchingDriverScreen({ route, navigation }: Props) {
  const { rideId } = route.params;
  const { ride } = useRidePolling(rideId);
  const clearRide = useRideStore((s) => s.clearRide);

  React.useEffect(() => {
    if (!ride) return;
    if (ride.status === "DRIVER_ASSIGNED" || ride.status === "ONGOING") {
      navigation.replace("LiveTracking", { rideId });
    }
  }, [ride, navigation, rideId]);

  const onRetry = async () => {
    await retryRide(rideId);
  };

  const onCancel = async () => {
    await cancelRide(rideId);
    clearRide();
    navigation.getParent()?.goBack();
  };

  if (ride?.status === "NO_DRIVER_AVAILABLE") {
    return (
      <YStack flex={1} justifyContent="center" alignItems="center" padding="$4" gap="$4">
        <Text fontSize="$6" fontWeight="700">
          No drivers found nearby
        </Text>
        <Button theme="active" onPress={onRetry}>
          Search again
        </Button>
        <Button chromeless onPress={onCancel}>
          Cancel
        </Button>
      </YStack>
    );
  }

  return (
    <YStack flex={1} justifyContent="center" alignItems="center" gap="$4">
      <Spinner size="large" />
      <Text fontSize="$6" fontWeight="600">
        Finding your driver…
      </Text>
      <Button chromeless onPress={onCancel}>
        Cancel
      </Button>
    </YStack>
  );
}
