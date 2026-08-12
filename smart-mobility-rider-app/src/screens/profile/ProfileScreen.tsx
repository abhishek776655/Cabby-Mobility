import React, { useEffect, useState } from "react";
import { YStack, XStack, Text, Button, Spinner, ListItem, Input } from "tamagui";
import { getMe, updatePreferences, getLocations, addLocation, deleteLocation } from "@/api/riders";
import { useAuthStore } from "@/store/authStore";
import type { PreferredPaymentMethod, RiderProfile, SavedLocation } from "@/api/types";

const PAYMENT_METHODS: PreferredPaymentMethod[] = ["CASH", "CARD", "WALLET"];

export function ProfileScreen() {
  const logout = useAuthStore((s) => s.logout);
  const [profile, setProfile] = useState<RiderProfile | null>(null);
  const [locations, setLocations] = useState<SavedLocation[]>([]);
  const [newLabel, setNewLabel] = useState("");
  const [newAddress, setNewAddress] = useState("");
  const [loadError, setLoadError] = useState(false);

  const refresh = () => {
    setLoadError(false);
    getMe()
      .then(setProfile)
      .catch(() => setLoadError(true));
    getLocations().then(setLocations).catch(() => {});
  };

  useEffect(refresh, []);

  const onChangePaymentMethod = async (method: PreferredPaymentMethod) => {
    const updated = await updatePreferences(method);
    setProfile(updated);
  };

  const onAddLocation = async () => {
    if (!newLabel || !newAddress) return;
    // NOTE: no geocoding available here (no Places API key wired) — this stores a placeholder
    // 0,0 coordinate. Replace with real geocoding once a Maps/Places key is provisioned.
    await addLocation({ label: newLabel, address: newAddress, latitude: 0, longitude: 0 });
    setNewLabel("");
    setNewAddress("");
    refresh();
  };

  if (loadError) {
    return (
      <YStack flex={1} justifyContent="center" alignItems="center" gap="$4" padding="$4">
        <Text color="$red10" textAlign="center">
          Couldn't load your profile. Your session may be stale.
        </Text>
        <Button theme="red_active" onPress={logout}>
          Log out
        </Button>
      </YStack>
    );
  }

  if (!profile) {
    return (
      <YStack flex={1} justifyContent="center" alignItems="center">
        <Spinner size="large" />
      </YStack>
    );
  }

  return (
    <YStack flex={1} padding="$4" gap="$4">
      <Text fontSize="$7" fontWeight="700">
        Profile
      </Text>
      <Text color="$gray10">Rating: {profile.rating != null ? profile.rating.toFixed(1) : "No rides yet"}</Text>

      <Text fontWeight="600">Payment method</Text>
      <XStack gap="$2">
        {PAYMENT_METHODS.map((method) => (
          <Button
            key={method}
            size="$3"
            theme={profile.preferredPaymentMethod === method ? "active" : undefined}
            onPress={() => onChangePaymentMethod(method)}
          >
            {method}
          </Button>
        ))}
      </XStack>

      <Text fontWeight="600" marginTop="$2">
        Saved locations
      </Text>
      {locations.map((loc) => (
        <ListItem
          key={loc.id}
          title={loc.label}
          subTitle={loc.address}
          onPress={() => deleteLocation(loc.id).then(refresh)}
          iconAfter={<Text color="$red10">Remove</Text>}
        />
      ))}
      <XStack gap="$2">
        <Input flex={1} placeholder="Label (Home)" value={newLabel} onChangeText={setNewLabel} />
        <Input flex={2} placeholder="Address" value={newAddress} onChangeText={setNewAddress} />
        <Button onPress={onAddLocation}>Add</Button>
      </XStack>

      <Button theme="red_active" marginTop="$4" onPress={logout}>
        Log out
      </Button>
    </YStack>
  );
}
