import React from "react";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import type { BookingStackParamList } from "./types";
import { PickupDropScreen } from "@/screens/booking/PickupDropScreen";
import { FareCompareScreen } from "@/screens/booking/FareCompareScreen";
import { ConfirmBookingScreen } from "@/screens/booking/ConfirmBookingScreen";
import { SearchingDriverScreen } from "@/screens/ride/SearchingDriverScreen";
import { LiveTrackingScreen } from "@/screens/ride/LiveTrackingScreen";
import { RideCompleteScreen } from "@/screens/ride/RideCompleteScreen";

const Stack = createNativeStackNavigator<BookingStackParamList>();

export function BookingStack() {
  return (
    <Stack.Navigator>
      <Stack.Screen name="PickupDrop" component={PickupDropScreen} options={{ title: "Where to?" }} />
      <Stack.Screen name="FareCompare" component={FareCompareScreen} options={{ title: "Choose a ride" }} />
      <Stack.Screen name="ConfirmBooking" component={ConfirmBookingScreen} options={{ title: "Confirm booking" }} />
      <Stack.Screen
        name="SearchingDriver"
        component={SearchingDriverScreen}
        options={{ title: "Finding your driver", headerBackVisible: false }}
      />
      <Stack.Screen
        name="LiveTracking"
        component={LiveTrackingScreen}
        options={{ title: "Your ride", headerBackVisible: false }}
      />
      <Stack.Screen name="RideComplete" component={RideCompleteScreen} options={{ title: "Trip complete" }} />
    </Stack.Navigator>
  );
}
