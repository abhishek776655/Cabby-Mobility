import React, { useEffect, useState } from "react";
import { YStack, Text, Input, Button, Spinner, ListItem } from "tamagui";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { BookingStackParamList } from "@/navigation/types";
import { getLocations } from "@/api/riders";
import type { SavedLocation } from "@/api/types";

type Props = NativeStackScreenProps<BookingStackParamList, "PickupDrop">;

/**
 * Address input relies on a third-party Places/Geocoding autocomplete API (Google Places,
 * matching react-native-maps' default provider) — not provided by this backend. This screen
 * ships with manual lat/lng entry plus saved-location quick-picks as the v1 fallback; wire a
 * real autocomplete widget here once a Maps/Places API key is available.
 */
export function PickupDropScreen({ navigation }: Props) {
  const [locations, setLocations] = useState<SavedLocation[]>([]);
  const [loading, setLoading] = useState(true);
  const [pickup, setPickup] = useState<SavedLocation | null>(null);
  const [drop, setDrop] = useState<SavedLocation | null>(null);

  useEffect(() => {
    getLocations()
      .then(setLocations)
      .finally(() => setLoading(false));
  }, []);

  const canContinue = pickup && drop;

  const onContinue = () => {
    if (!pickup || !drop) return;
    navigation.navigate("FareCompare", {
      pickupLat: pickup.latitude,
      pickupLng: pickup.longitude,
      dropLat: drop.latitude,
      dropLng: drop.longitude,
      pickupLocation: pickup.address,
      dropLocation: drop.address,
    });
  };

  if (loading) {
    return (
      <YStack flex={1} justifyContent="center" alignItems="center">
        <Spinner />
      </YStack>
    );
  }

  return (
    <YStack flex={1} padding="$4" gap="$3">
      <Text fontWeight="600">Pickup</Text>
      {locations.map((loc) => (
        <ListItem
          key={`pickup-${loc.id}`}
          title={loc.label}
          subTitle={loc.address}
          pressTheme
          backgroundColor={pickup?.id === loc.id ? "$blue4" : undefined}
          onPress={() => setPickup(loc)}
        />
      ))}
      <Text fontWeight="600" marginTop="$3">
        Drop
      </Text>
      {locations.map((loc) => (
        <ListItem
          key={`drop-${loc.id}`}
          title={loc.label}
          subTitle={loc.address}
          pressTheme
          backgroundColor={drop?.id === loc.id ? "$blue4" : undefined}
          onPress={() => setDrop(loc)}
        />
      ))}
      {locations.length === 0 && (
        <Text color="$gray10">Add a saved location from your profile to book a ride.</Text>
      )}
      <Button theme="active" disabled={!canContinue} onPress={onContinue}>
        See fares
      </Button>
    </YStack>
  );
}
