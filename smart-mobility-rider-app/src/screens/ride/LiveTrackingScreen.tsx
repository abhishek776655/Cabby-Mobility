import React, { useEffect } from "react";
import { YStack, Text, Button } from "tamagui";
import MapView, { Marker } from "react-native-maps";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { BookingStackParamList } from "@/navigation/types";
import { useRidePolling } from "@/hooks/useRidePolling";
import { useTripSocket } from "@/realtime/useTripSocket";
import { useRideStore } from "@/store/rideStore";
import { cancelRide } from "@/api/rides";

type Props = NativeStackScreenProps<BookingStackParamList, "LiveTracking">;

const CANCELLABLE_STATUSES = new Set(["REQUESTED", "MATCHING", "DRIVER_ASSIGNED"]);

export function LiveTrackingScreen({ route, navigation }: Props) {
  const { rideId } = route.params;
  const { ride } = useRidePolling(rideId);
  const { connected } = useTripSocket(rideId);
  const driverLocation = useRideStore((s) => s.driverLocation);
  const clearRide = useRideStore((s) => s.clearRide);

  useEffect(() => {
    if (ride?.status === "COMPLETED") {
      navigation.replace("RideComplete", { rideId });
    }
    if (ride?.status === "CANCELLED") {
      clearRide();
      navigation.getParent()?.goBack();
    }
  }, [ride, navigation, rideId, clearRide]);

  const onCancel = async () => {
    await cancelRide(rideId);
    clearRide();
    navigation.getParent()?.goBack();
  };

  return (
    <YStack flex={1}>
      <MapView
        style={{ flex: 1 }}
        initialRegion={
          ride
            ? {
                latitude: ride.pickupLatitude,
                longitude: ride.pickupLongitude,
                latitudeDelta: 0.05,
                longitudeDelta: 0.05,
              }
            : undefined
        }
      >
        {ride && (
          <Marker
            coordinate={{ latitude: ride.pickupLatitude, longitude: ride.pickupLongitude }}
            title="Pickup"
            pinColor="green"
          />
        )}
        {ride && (
          <Marker
            coordinate={{ latitude: ride.dropLatitude, longitude: ride.dropLongitude }}
            title="Drop"
            pinColor="red"
          />
        )}
        {driverLocation && (
          <Marker
            coordinate={{ latitude: driverLocation.latitude, longitude: driverLocation.longitude }}
            title="Your driver"
            pinColor="blue"
          />
        )}
      </MapView>
      {!connected && (
        <YStack position="absolute" top="$3" left="$4" right="$4" backgroundColor="$yellow4" padding="$2" borderRadius="$3">
          <Text fontSize="$2" textAlign="center">
            Reconnecting to live tracking…
          </Text>
        </YStack>
      )}
      <YStack position="absolute" bottom="$6" left="$4" right="$4" gap="$2">
        <Text textAlign="center" backgroundColor="$background" padding="$2" borderRadius="$3">
          {ride?.status ?? "Loading…"}
        </Text>
        {ride && CANCELLABLE_STATUSES.has(ride.status) && (
          <Button theme="red_active" onPress={onCancel}>
            Cancel ride
          </Button>
        )}
      </YStack>
    </YStack>
  );
}
