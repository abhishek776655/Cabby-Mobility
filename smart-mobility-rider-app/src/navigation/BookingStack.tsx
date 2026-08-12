import React from "react";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import type { BookingStackParamList } from "./types";
import { AddressSearchScreen } from "@/screens/booking/AddressSearchScreen";
import { FareCompareScreen } from "@/screens/booking/FareCompareScreen";
import { ConfirmBookingScreen } from "@/screens/booking/ConfirmBookingScreen";
import { SearchingDriverScreen } from "@/screens/ride/SearchingDriverScreen";
import { LiveTrackingScreen } from "@/screens/ride/LiveTrackingScreen";
import { RideCompleteScreen } from "@/screens/ride/RideCompleteScreen";

const Stack = createNativeStackNavigator<BookingStackParamList>();

export function BookingStack() {
  return (
    <Stack.Navigator>
      {/* Entry point of the booking flow: owns its own header and search fields. Rises from
          the bottom so it reads as the keyboard and sheet arriving together, rather than a
          sideways page push. */}
      <Stack.Screen
        name="AddressSearch"
        component={AddressSearchScreen}
        options={{ headerShown: false, animation: "slide_from_bottom" }}
      />
      <Stack.Screen name="FareCompare" component={FareCompareScreen} options={{ headerShown: false }} />
      <Stack.Screen name="ConfirmBooking" component={ConfirmBookingScreen} options={{ title: "Confirm booking" }} />
      <Stack.Screen
        name="SearchingDriver"
        component={SearchingDriverScreen}
        options={{ title: "Finding your driver", headerBackVisible: false }}
      />
      <Stack.Screen name="LiveTracking" component={LiveTrackingScreen} options={{ headerShown: false }} />
      <Stack.Screen name="RideComplete" component={RideCompleteScreen} options={{ title: "Trip complete" }} />
    </Stack.Navigator>
  );
}
