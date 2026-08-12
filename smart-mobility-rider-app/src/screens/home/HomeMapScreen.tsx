import React, { useEffect, useRef, useState } from "react";
import { Animated, Easing, Pressable, StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useIsFocused } from "@react-navigation/native";
import MapView from "react-native-maps";
import * as Location from "expo-location";
import { Ionicons } from "@expo/vector-icons";
import type { BottomTabScreenProps } from "@react-navigation/bottom-tabs";
import type { CompositeScreenProps } from "@react-navigation/native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { AppTabsParamList, RootStackParamList } from "@/navigation/types";
import { useLocationPermission } from "@/hooks/useLocationPermission";
import { useRideStore } from "@/store/rideStore";
import { useRecentSearchesStore } from "@/store/recentSearchesStore";
import type { PickedAddress } from "@/store/addressPickerStore";
import { getRide } from "@/api/rides";
import { MAP_DEFAULT_CENTER, MAP_DEFAULT_DELTA } from "@/constants/mapDefaults";
import { colors, radii, spacing } from "@/constants/theme";

type Props = CompositeScreenProps<
  BottomTabScreenProps<AppTabsParamList, "Home">,
  NativeStackScreenProps<RootStackParamList>
>;

const TERMINAL_STATUSES = new Set(["COMPLETED", "CANCELLED"]);
// Roughly a few streets across — close enough to recognise where you are.
const HOME_ZOOM_DELTA = 0.008;
// Enough to be useful without turning Home into a list screen.
const HOME_RECENT_COUNT = 3;
const PANEL_EXIT_MS = 260;
// Head start for the panel so it is already moving when the planner starts rising; the two
// then overlap instead of playing back to back.
const PANEL_EXIT_LEAD_MS = 90;

export function HomeMapScreen({ navigation }: Props) {
  const { granted } = useLocationPermission();
  const activeRideId = useRideStore((s) => s.activeRideId);
  const rideStatus = useRideStore((s) => s.rideStatus);
  const clearRide = useRideStore((s) => s.clearRide);
  const isFocused = useIsFocused();
  const mapRef = useRef<MapView>(null);
  const recents = useRecentSearchesStore((s) => s.recents);

  // Panel exit. 0 = resting, 1 = slid off the bottom. Home stays mounted under the booking
  // stack, so without this the panel would just sit there while the next screen rises over it.
  const panelAnim = useRef(new Animated.Value(0)).current;
  const [panelHeight, setPanelHeight] = useState(0);

  // Returning to Home (back out of booking, or via the tab bar) must restore the panel — it
  // is left in its exited position by the transition out.
  useEffect(() => {
    if (!isFocused) return;
    Animated.timing(panelAnim, {
      toValue: 0,
      duration: 220,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [isFocused, panelAnim]);

  // Open on the rider's own street rather than a city overview — Home's job is "where am I,
  // and where am I going from here". Runs once: re-centring later would fight their panning.
  const centredOnRider = useRef(false);
  useEffect(() => {
    if (!granted || centredOnRider.current) return;
    centredOnRider.current = true;
    Location.getCurrentPositionAsync()
      .then((pos) =>
        mapRef.current?.animateToRegion(
          {
            latitude: pos.coords.latitude,
            longitude: pos.coords.longitude,
            latitudeDelta: HOME_ZOOM_DELTA,
            longitudeDelta: HOME_ZOOM_DELTA,
          },
          600
        )
      )
      .catch(() => {
        // Keep the default city view; a failed fix is not worth surfacing on Home.
      });
  }, [granted]);

  // activeRideId/rideStatus persist across app restarts (rideStore.ts partialize), so a ride
  // that finished or got cancelled while the app was closed/backgrounded leaves Home stuck
  // offering "Resume your ride" forever. Reconcile against the real backend status whenever
  // Home comes into focus and self-heal instead of trapping the rider.
  useEffect(() => {
    if (!isFocused || !activeRideId) return;
    getRide(activeRideId)
      .then((ride) => {
        if (TERMINAL_STATUSES.has(ride.status)) {
          clearRide();
        }
      })
      .catch(() => {
        // Ride lookup failing (e.g. 404 for a ride from a wiped test backend) shouldn't
        // leave the rider stuck either — drop the stale reference.
        clearRide();
      });
  }, [isFocused, activeRideId, clearRide]);

  // Only rendered when a ride is in flight, so there is no "else book one" branch — that is
  // the search card's job.
  const resumeRide = () => {
    if (!activeRideId) return;
    navigation.navigate("Booking", {
      screen: rideStatus === "NO_DRIVER_AVAILABLE" ? "SearchingDriver" : "LiveTracking",
      params: { rideId: activeRideId },
    });
  };

  // Slides the panel out, then hands off to whichever booking screen the caller wants — the
  // two movements read as one handover rather than a sequence. Navigating first would freeze
  // the panel mid-exit, since the screen stops rendering once it is covered.
  const exitToBooking = (go: () => void) => {
    Animated.timing(panelAnim, {
      toValue: 1,
      duration: PANEL_EXIT_MS,
      easing: Easing.in(Easing.cubic),
      useNativeDriver: true,
    }).start();
    setTimeout(go, PANEL_EXIT_LEAD_MS);
  };

  const searchDestination = () => {
    exitToBooking(() => {
      navigation.navigate("Booking", { screen: "AddressSearch", params: { field: "drop" } });
    });
  };

  // A recent destination is a one-tap trip from wherever the rider is right now — the planner
  // (which asks for pickup too) would be a step backwards for the exact case recents exist to
  // shortcut. "Your current location" matches the same regex FareCompare already uses to
  // resolve a live position into a real name for its header bubble, so this doesn't duplicate
  // that reverse-geocode logic here.
  const openRecent = (place: PickedAddress) => {
    if (!granted) {
      // No fix to build a "from here" trip with — fall back to the normal planner instead of
      // silently failing.
      searchDestination();
      return;
    }
    exitToBooking(async () => {
      try {
        const pos = await Location.getCurrentPositionAsync();
        navigation.navigate("Booking", {
          screen: "FareCompare",
          params: {
            pickupLat: pos.coords.latitude,
            pickupLng: pos.coords.longitude,
            pickupLocation: "Your current location",
            dropLat: place.latitude,
            dropLng: place.longitude,
            dropLocation: place.address,
          },
        });
      } catch {
        // GPS failed at the moment of tapping — the planner can still get them there, it just
        // asks for pickup too.
        navigation.navigate("Booking", { screen: "AddressSearch", params: { field: "drop" } });
      }
    });
  };

  return (
    <View style={styles.container}>
      <MapView
        ref={mapRef}
        style={styles.map}
        showsUserLocation={granted}
        showsMyLocationButton={false}
        initialRegion={{
          latitude: MAP_DEFAULT_CENTER.latitude,
          longitude: MAP_DEFAULT_CENTER.longitude,
          ...MAP_DEFAULT_DELTA,
        }}
      />

      {activeRideId ? (
        <SafeAreaView style={styles.bottomOverlay} edges={["bottom"]} pointerEvents="box-none">
          <Pressable style={styles.ctaButton} onPress={resumeRide}>
            <Ionicons name="navigate" size={18} color={colors.primaryText} />
            <Text style={styles.ctaText}>Resume your ride</Text>
          </Pressable>
        </SafeAreaView>
      ) : (
        // Sits in the lower half, within thumb reach, and leaves the upper map clear — the
        // rider's own position is what they're orienting against before they type.
        <Animated.View
          onLayout={(e) => setPanelHeight(e.nativeEvent.layout.height)}
          style={[
            styles.panel,
            {
              transform: [
                {
                  translateY: panelAnim.interpolate({
                    inputRange: [0, 1],
                    // Falls by its own height, so it clears the screen whatever it contains.
                    outputRange: [0, panelHeight || 320],
                  }),
                },
              ],
            },
          ]}
        >
          <SafeAreaView edges={["bottom"]}>
          <Pressable style={styles.searchCard} onPress={searchDestination}>
            <View style={styles.searchDot} />
            <Text style={styles.searchText}>Where to?</Text>
            <Ionicons name="search" size={18} color={colors.inkMuted} />
          </Pressable>

          {recents.slice(0, HOME_RECENT_COUNT).map((place, index) => (
            <Pressable
              key={`${place.latitude},${place.longitude}`}
              style={({ pressed }) => [
                styles.recentRow,
                index < Math.min(recents.length, HOME_RECENT_COUNT) - 1 && styles.recentRowDivided,
                pressed && styles.recentRowPressed,
              ]}
              onPress={() => openRecent(place)}
              accessibilityRole="button"
              accessibilityLabel={`Go to ${place.label}`}
            >
              <View style={styles.recentIcon}>
                <Ionicons name="time-outline" size={16} color={colors.ink} />
              </View>
              <View style={styles.recentText}>
                <Text style={styles.recentLabel} numberOfLines={1}>
                  {place.label}
                </Text>
                {place.address !== place.label && (
                  <Text style={styles.recentAddress} numberOfLines={1}>
                    {place.address}
                  </Text>
                )}
              </View>
            </Pressable>
          ))}
          </SafeAreaView>
        </Animated.View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  map: {
    flex: 1,
  },
  panel: {
    position: "absolute",
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: colors.bg,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    paddingHorizontal: spacing.md,
    paddingTop: spacing.lg,
    paddingBottom: spacing.sm,
    shadowColor: colors.ink,
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.12,
    shadowRadius: 16,
    elevation: 12,
  },
  searchCard: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    backgroundColor: colors.surfaceSunken,
    borderRadius: radii.md,
    paddingHorizontal: 16,
    paddingVertical: 18,
    marginBottom: spacing.sm,
  },
  searchDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.accent,
  },
  searchText: {
    flex: 1,
    fontSize: 16,
    fontWeight: "600",
    color: colors.inkMuted,
  },
  recentRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingVertical: 14,
  },
  recentRowDivided: {
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  recentRowPressed: {
    opacity: 0.6,
  },
  recentIcon: {
    width: 34,
    height: 34,
    borderRadius: radii.pill,
    backgroundColor: colors.surfaceSunken,
    alignItems: "center",
    justifyContent: "center",
  },
  recentText: {
    flex: 1,
    gap: 1,
  },
  recentLabel: {
    fontSize: 15,
    fontWeight: "600",
    color: colors.ink,
  },
  recentAddress: {
    fontSize: 13,
    color: colors.inkMuted,
  },
  bottomOverlay: {
    position: "absolute",
    bottom: 0,
    left: 0,
    right: 0,
    paddingHorizontal: spacing.md,
    paddingBottom: spacing.sm,
  },
  ctaButton: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 10,
    height: 56,
    borderRadius: radii.lg,
    backgroundColor: colors.primary,
    shadowColor: colors.primary,
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.2,
    shadowRadius: 14,
    elevation: 4,
  },
  ctaText: {
    fontSize: 16,
    fontWeight: "700",
    color: colors.primaryText,
  },
});
