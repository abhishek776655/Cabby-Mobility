import React from "react";
import { YStack, Text } from "tamagui";

/**
 * cab-service has no "list my rides" endpoint today (only GET /rides/{rideId} by id) — so
 * there is no real ride history to show yet. This is an honest empty state, not a stub UI
 * wired to a non-existent endpoint. Add a backend endpoint (e.g. GET /rides?riderUserId=)
 * before building this out.
 */
export function RideHistoryScreen() {
  return (
    <YStack flex={1} justifyContent="center" alignItems="center" padding="$4" gap="$2">
      <Text fontSize="$6" fontWeight="600">
        Ride history coming soon
      </Text>
      <Text color="$gray10" textAlign="center">
        The backend doesn't have a "list my rides" endpoint yet.
      </Text>
    </YStack>
  );
}
