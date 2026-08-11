import React, { useEffect, useState } from "react";
import { NavigationContainer } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { useAuthStore } from "@/store/authStore";
import { useRideStore } from "@/store/rideStore";
import { getRide } from "@/api/rides";
import { AuthStack } from "./AuthStack";
import { AppTabs } from "./AppTabs";
import { BookingStack } from "./BookingStack";
import type { RootStackParamList } from "./types";

const Stack = createNativeStackNavigator<RootStackParamList>();

export function RootNavigator() {
  const status = useAuthStore((s) => s.status);
  const activeRideId = useRideStore((s) => s.activeRideId);
  const clearRide = useRideStore((s) => s.clearRide);
  const [resuming, setResuming] = useState(!!activeRideId);

  // On cold start, if there's a persisted active ride, confirm it's still live before
  // resuming into the booking stack — handles the app being killed mid-ride.
  useEffect(() => {
    if (!activeRideId) {
      setResuming(false);
      return;
    }
    getRide(activeRideId)
      .then((ride) => {
        if (ride.status === "COMPLETED" || ride.status === "CANCELLED") {
          clearRide();
        }
      })
      .catch(() => clearRide())
      .finally(() => setResuming(false));
  }, [activeRideId, clearRide]);

  if (status !== "authenticated") {
    return (
      <NavigationContainer>
        <AuthStack />
      </NavigationContainer>
    );
  }

  if (resuming) {
    return null;
  }

  return (
    <NavigationContainer>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        <Stack.Screen name="App" component={AppTabs} />
        <Stack.Screen name="Booking" component={BookingStack} options={{ headerShown: false }} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
