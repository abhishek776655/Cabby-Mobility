import React, { useEffect, useState } from "react";
import { YStack, XStack, Text, Button, Spinner, Card } from "tamagui";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { BookingStackParamList } from "@/navigation/types";
import { quoteAll } from "@/api/fares";
import { useRideStore } from "@/store/rideStore";
import { toApiError, type ApiError } from "@/api/client";
import type { QuoteAllResponse, VehicleQuote } from "@/api/types";

type Props = NativeStackScreenProps<BookingStackParamList, "FareCompare">;

export function FareCompareScreen({ route, navigation }: Props) {
  const { pickupLat, pickupLng, dropLat, dropLng, pickupLocation, dropLocation } = route.params;
  const setFareQuote = useRideStore((s) => s.setFareQuote);

  const [quote, setQuote] = useState<QuoteAllResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(() => {
    quoteAll({ pickupLat, pickupLng, dropLat, dropLng })
      .then((result) => {
        setQuote(result);
        setFareQuote(result);
      })
      .catch((e) => setError(toApiError(e)))
      .finally(() => setLoading(false));
  }, [pickupLat, pickupLng, dropLat, dropLng, setFareQuote]);

  const onSelect = (vehicleQuote: VehicleQuote) => {
    navigation.navigate("ConfirmBooking", {
      pickupLat,
      pickupLng,
      dropLat,
      dropLng,
      pickupLocation,
      dropLocation,
      quote: vehicleQuote,
    });
  };

  if (loading) {
    return (
      <YStack flex={1} justifyContent="center" alignItems="center">
        <Spinner size="large" />
      </YStack>
    );
  }

  if (error || !quote) {
    return (
      <YStack flex={1} justifyContent="center" alignItems="center" padding="$4" gap="$3">
        <Text color="$red10">{error?.message ?? "Could not load fares"}</Text>
        <Button onPress={() => navigation.replace("FareCompare", route.params)}>Retry</Button>
      </YStack>
    );
  }

  return (
    <YStack flex={1} padding="$4" gap="$3">
      <Text color="$gray10">
        {(quote.distanceMeters / 1000).toFixed(1)} km · {Math.round(quote.durationSeconds / 60)} min
      </Text>
      {quote.quotes.map((q) => (
        <Card key={q.vehicleType} bordered padding="$4" pressStyle={{ opacity: 0.8 }} onPress={() => onSelect(q)}>
          <XStack justifyContent="space-between" alignItems="center">
            <YStack>
              <Text fontWeight="700" fontSize="$5">
                {q.vehicleType}
              </Text>
              {q.breakdown.surgeMultiplier > 1 && (
                <Text color="$orange10" fontSize="$2">
                  {q.breakdown.surgeMultiplier}x surge
                </Text>
              )}
            </YStack>
            <Text fontWeight="700" fontSize="$6">
              {quote.currency} {(q.breakdown.total / 100).toFixed(2)}
            </Text>
          </XStack>
        </Card>
      ))}
    </YStack>
  );
}
