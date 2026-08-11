import React from "react";
import { YStack, Text, Button } from "tamagui";
import MapView from "react-native-maps";
import type { BottomTabScreenProps } from "@react-navigation/bottom-tabs";
import type { CompositeScreenProps } from "@react-navigation/native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { AppTabsParamList, RootStackParamList } from "@/navigation/types";
import { useLocationPermission } from "@/hooks/useLocationPermission";
import { useRideStore } from "@/store/rideStore";

type Props = CompositeScreenProps<
  BottomTabScreenProps<AppTabsParamList, "Home">,
  NativeStackScreenProps<RootStackParamList>
>;

export function HomeMapScreen({ navigation }: Props) {
  const { granted } = useLocationPermission();
  const activeRideId = useRideStore((s) => s.activeRideId);
  const rideStatus = useRideStore((s) => s.rideStatus);

  const resumeOrBook = () => {
    if (activeRideId && rideStatus === "NO_DRIVER_AVAILABLE") {
      navigation.navigate("Booking", { screen: "SearchingDriver" });
    } else if (activeRideId) {
      navigation.navigate("Booking", { screen: "LiveTracking" });
    } else {
      navigation.navigate("Booking", { screen: "PickupDrop" });
    }
  };

  return (
    <YStack flex={1}>
      <MapView style={{ flex: 1 }} showsUserLocation={granted} />
      <YStack position="absolute" bottom="$6" left="$4" right="$4">
        <Button size="$5" theme="active" onPress={resumeOrBook}>
          {activeRideId ? "Resume your ride" : "Where to?"}
        </Button>
      </YStack>
    </YStack>
  );
}
