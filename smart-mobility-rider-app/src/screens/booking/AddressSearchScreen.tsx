import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Animated,
  FlatList,
  Keyboard,
  PanResponder,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
  useWindowDimensions,
} from "react-native";
import { SafeAreaView, useSafeAreaInsets } from "react-native-safe-area-context";
import MapView, { Marker, type Region } from "react-native-maps";
import * as Location from "expo-location";
import { Ionicons } from "@expo/vector-icons";
import { MAP_DEFAULT_CENTER, MAP_DEFAULT_DELTA } from "@/constants/mapDefaults";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { BookingStackParamList } from "@/navigation/types";
import { getLocations } from "@/api/riders";
import { reverseGeocode } from "@/api/geocode";
import type { GeocodeSuggestion, SavedLocation } from "@/api/types";
import { useAddressAutocomplete } from "@/hooks/useAddressAutocomplete";
import { useLocationPermission } from "@/hooks/useLocationPermission";
import { useAddressPickerStore, type PickedAddress } from "@/store/addressPickerStore";
import { useRecentSearchesStore } from "@/store/recentSearchesStore";
import { SearchField } from "@/components/ui/SearchField";
import { PrimaryButton } from "@/components/ui/PrimaryButton";
import { AddressSuggestionRow } from "@/components/booking/AddressSuggestionRow";
import { colors, radii, spacing } from "@/constants/theme";

type Props = NativeStackScreenProps<BookingStackParamList, "AddressSearch">;

type Row =
  | { type: "suggestion"; key: string; suggestion: GeocodeSuggestion }
  | { type: "recent"; key: string; address: PickedAddress }
  | { type: "saved"; key: string; location: SavedLocation }
  | { type: "current"; key: string }
  | { type: "map"; key: string }
  | { type: "header"; key: string; title: string };

const HEADING_RISE = 14;
// Mirrors FareCompare's sheet: the map is a strip whose height the drag gesture changes, and
// the sheet is a flex sibling that takes whatever is left. Mounting at the minimum means the
// sheet starts fully expanded — the rider came here to type, not to look at a map.
const MAP_HEIGHT_MOUNTED = 96;
// What the sheet still needs when dragged fully down, in its collapsed pin-picker form. It is
// the sum of the real content, not a round number:
//   header  8 + handle 4 + 4 + (title 22 + 12) + 4 + subtitle 18 + 14 = 86
//   separator                                                          =  1
//   card    8 + address 56 + 16 + button 54 + 8                        = 142
// Any surplus over this shows up as a gap under the subtitle, because the collapsed card is
// bottom-aligned and absorbs all the slack in one place.
const SHEET_MIN_CONTENT_HEIGHT = 229;
const DRAG_HEADER_HEIGHT = 44;
// Fraction of the drag travel past which the sheet is treated as collapsed — i.e. the rider
// has given ~70% of the screen to the map and is working with it rather than with the list.
const COLLAPSE_AT = 0.7;
// Street-level span for pin placement — a city-wide view makes the pin useless for precision.
const PIN_ZOOM_DELTA = 0.004;

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

export function AddressSearchScreen({ route, navigation }: Props) {
  const { field, mode = "plan", pickup: pickupParam, drop: dropParam } = route.params;
  const submitToPicker = useAddressPickerStore((s) => s.submit);
  const recents = useRecentSearchesStore((s) => s.recents);
  const remember = useRecentSearchesStore((s) => s.remember);
  const { granted: locationGranted } = useLocationPermission();

  // In plan mode this screen owns the whole trip and hands a complete one to FareCompare.
  // In edit mode it resolves a single leg and hands it back through the picker store.
  const [pickup, setPickup] = useState<PickedAddress | null>(pickupParam ?? null);
  const [drop, setDrop] = useState<PickedAddress | null>(dropParam ?? null);
  const [pickupQuery, setPickupQuery] = useState("");
  const [dropQuery, setDropQuery] = useState("");
  const [activeField, setActiveField] = useState(field);
  const [savedLocations, setSavedLocations] = useState<SavedLocation[]>([]);
  const [locatingPickup, setLocatingPickup] = useState(false);

  const pickupRef = useRef<TextInput>(null);
  const dropRef = useRef<TextInput>(null);
  const mapRef = useRef<MapView>(null);
  const headingAnim = useRef(new Animated.Value(0)).current;

  const { height: windowHeight } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  // The home indicator eats into the sheet, so the drag limit has to account for it or the
  // confirm button ends up partly under it.
  const mapHeightMax = Math.max(
    MAP_HEIGHT_MOUNTED,
    windowHeight - SHEET_MIN_CONTENT_HEIGHT - insets.bottom
  );
  const mapHeight = useRef(new Animated.Value(MAP_HEIGHT_MOUNTED)).current;
  const dragStartHeight = useRef(MAP_HEIGHT_MOUNTED);
  const currentMapHeight = useRef(MAP_HEIGHT_MOUNTED);
  // PanResponder.create runs once, so its callbacks would close over the first render's
  // mapHeightMax; windowHeight can change (rotation, split view) after that.
  const mapHeightMaxRef = useRef(mapHeightMax);

  useEffect(() => {
    mapHeightMaxRef.current = mapHeightMax;
  }, [mapHeightMax]);

  // Dragged (nearly) all the way down, the sheet is too short for two fields plus a list. It
  // switches to a compact form: just "Where to?" and a way to place the pin on the map that
  // the drag has just revealed.
  const [collapsed, setCollapsed] = useState(false);
  // onRegionChangeComplete is a stable callback held by the map, so it can't read `collapsed`
  // from a closure without going stale.
  const collapsedRef = useRef(false);
  useEffect(() => {
    const id = mapHeight.addListener(({ value }) => {
      currentMapHeight.current = value;
      const travelled = (value - MAP_HEIGHT_MOUNTED) / Math.max(1, mapHeightMax - MAP_HEIGHT_MOUNTED);
      const next = travelled >= COLLAPSE_AT;
      collapsedRef.current = next;
      setCollapsed(next);
    });
    return () => mapHeight.removeListener(id);
  }, [mapHeight, mapHeightMax]);

  const panResponder = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_evt, gesture) => Math.abs(gesture.dy) > 4,
      onPanResponderGrant: () => {
        // Dragging the sheet down is a deliberate move to see the map; the keyboard would
        // otherwise cover most of what the gesture just revealed.
        Keyboard.dismiss();
        dragStartHeight.current = currentMapHeight.current;
      },
      onPanResponderMove: (_evt, gesture) => {
        mapHeight.setValue(
          clamp(dragStartHeight.current + gesture.dy, MAP_HEIGHT_MOUNTED, mapHeightMaxRef.current)
        );
      },
      onPanResponderRelease: (_evt, gesture) => {
        const max = mapHeightMaxRef.current;
        const midpoint = (MAP_HEIGHT_MOUNTED + max) / 2;
        let target = currentMapHeight.current > midpoint ? max : MAP_HEIGHT_MOUNTED;
        if (gesture.vy < -0.6) target = MAP_HEIGHT_MOUNTED;
        else if (gesture.vy > 0.6) target = max;
        Animated.spring(mapHeight, {
          toValue: target,
          useNativeDriver: false,
          bounciness: 4,
        }).start();
      },
    })
  ).current;

  const query = activeField === "pickup" ? pickupQuery : dropQuery;
  const { suggestions, loading, error } = useAddressAutocomplete(query);

  useEffect(() => {
    getLocations()
      .then(setSavedLocations)
      .catch(() => setSavedLocations([]));
  }, []);

  // Heading rises into place on mount — the one authored motion moment on this screen.
  useEffect(() => {
    Animated.timing(headingAnim, {
      toValue: 1,
      duration: 320,
      useNativeDriver: true,
    }).start();
  }, [headingAnim]);

  // Resolve the rider's position for the pickup leg so they only have to think about where
  // they're going. Never substitutes a placeholder when it fails — an unset leg stays unset.
  //
  // Guarded by a ref rather than by `pickup` being empty: keying off the empty state made
  // clearing the field re-trigger the lookup, which instantly refilled it and left the clear
  // button looking broken.
  const pickupAutofilled = useRef(false);
  useEffect(() => {
    if (mode !== "plan" || pickup || !locationGranted || pickupAutofilled.current) return;
    pickupAutofilled.current = true;
    setLocatingPickup(true);
    Location.getCurrentPositionAsync()
      .then((pos) =>
        setPickup({
          label: "Current location",
          address: "Your current location",
          latitude: pos.coords.latitude,
          longitude: pos.coords.longitude,
        })
      )
      .catch(() => {})
      .finally(() => setLocatingPickup(false));
  }, [mode, pickup, locationGranted]);

  useEffect(() => {
    const target = field === "pickup" ? pickupRef : dropRef;
    // Delay past the push transition — focusing mid-animation drops the keyboard on Android.
    const timer = setTimeout(() => target.current?.focus(), 350);
    return () => clearTimeout(timer);
  }, [field]);

  const goToFares = useCallback(
    (from: PickedAddress, to: PickedAddress) => {
      Keyboard.dismiss();
      navigation.navigate("FareCompare", {
        pickupLat: from.latitude,
        pickupLng: from.longitude,
        dropLat: to.latitude,
        dropLng: to.longitude,
        pickupLocation: from.address,
        dropLocation: to.address,
      });
    },
    [navigation]
  );

  /** Applies a chosen place to whichever leg is active, then decides where the rider goes. */
  const choose = useCallback(
    (picked: PickedAddress) => {
      // History is a list of places the rider goes, so only destinations are kept. Pickups are
      // overwhelmingly "wherever I am now", which is not a place worth offering again.
      if (activeField === "drop") {
        remember(picked);
      }

      if (mode === "edit") {
        submitToPicker(picked);
        Keyboard.dismiss();
        navigation.goBack();
        return;
      }

      const nextPickup = activeField === "pickup" ? picked : pickup;
      const nextDrop = activeField === "drop" ? picked : drop;
      if (activeField === "pickup") {
        setPickup(picked);
        setPickupQuery("");
      } else {
        setDrop(picked);
        setDropQuery("");
      }

      if (nextPickup && nextDrop) {
        goToFares(nextPickup, nextDrop);
        return;
      }
      // Only one leg known — move focus to the one still missing rather than stalling.
      const missing = nextPickup ? "drop" : "pickup";
      setActiveField(missing);
      (missing === "pickup" ? pickupRef : dropRef).current?.focus();
    },
    [mode, activeField, pickup, drop, submitToPicker, navigation, goToFares, remember]
  );

  const useCurrentLocation = useCallback(() => {
    if (!locationGranted) return;
    setLocatingPickup(true);
    Location.getCurrentPositionAsync()
      .then((pos) =>
        choose({
          label: "Current location",
          address: "Your current location",
          latitude: pos.coords.latitude,
          longitude: pos.coords.longitude,
        })
      )
      .catch(() => {})
      .finally(() => setLocatingPickup(false));
  }, [locationGranted, choose]);

  // --- Centre-pin placement, active while the sheet is collapsed ---------------------------
  // The map fills the screen at that point, so the pin is placed by dragging the map under a
  // fixed centre marker rather than on a separate screen.
  const [pinAddress, setPinAddress] = useState<PickedAddress | null>(null);
  const [pinResolving, setPinResolving] = useState(false);
  const [pinOutOfArea, setPinOutOfArea] = useState(false);
  const pinAbortRef = useRef<AbortController | null>(null);

  const onRegionSettled = useCallback(
    (region: Region) => {
      // Only meaningful while the pin is on screen; otherwise this fires for ordinary panning.
      if (!collapsedRef.current) return;

      pinAbortRef.current?.abort();
      const controller = new AbortController();
      pinAbortRef.current = controller;
      setPinResolving(true);

      reverseGeocode(region.latitude, region.longitude, controller.signal)
        .then((result) => {
          if (controller.signal.aborted) return;
          setPinOutOfArea(result === null);
          setPinAddress(
            result
              ? {
                  label: result.label,
                  address: result.description ? `${result.label}, ${result.description}` : result.label,
                  latitude: region.latitude,
                  longitude: region.longitude,
                }
              : null
          );
        })
        .catch((e: unknown) => {
          if (controller.signal.aborted) return;
          const name = (e as { name?: string } | null)?.name;
          if (name === "CanceledError" || name === "AbortError") return;
          setPinAddress(null);
          setPinOutOfArea(false);
        })
        .finally(() => {
          if (!controller.signal.aborted) setPinResolving(false);
        });
    },
    []
  );

  useEffect(() => () => pinAbortRef.current?.abort(), []);

  /** Springs the sheet back up — used when the rider taps into a field while collapsed. */
  const expandSheet = useCallback(() => {
    Animated.spring(mapHeight, {
      toValue: MAP_HEIGHT_MOUNTED,
      useNativeDriver: false,
      bounciness: 4,
    }).start();
  }, [mapHeight]);

  /** Raises the sheet and puts the cursor back in the field for the leg being placed. */
  const returnToTyping = useCallback(() => {
    expandSheet();
    // After the spring, or the focus lands while the field is still off-screen and the
    // keyboard opens over a sheet that is still moving.
    setTimeout(() => {
      (activeField === "pickup" ? pickupRef : dropRef).current?.focus();
    }, 220);
  }, [expandSheet, activeField]);

  /**
   * Places the pin by handing the screen over to the map, rather than pushing a separate
   * picker: the collapsed sheet already is that picker.
   */
  const openMapPicker = useCallback(() => {
    Keyboard.dismiss();
    Animated.spring(mapHeight, {
      toValue: mapHeightMaxRef.current,
      useNativeDriver: false,
      bounciness: 4,
    }).start();
  }, [mapHeight]);

  // Entering pin mode, drop the camera on the leg being placed (or the other one, as the best
  // available guess) and zoom in — a pin over a city-wide view can't be placed accurately, and
  // an already-chosen destination is the obvious thing to be adjusting.
  const seededPinFor = useRef<string | null>(null);
  useEffect(() => {
    if (!collapsed) {
      seededPinFor.current = null;
      return;
    }
    const anchor = (activeField === "drop" ? drop : pickup) ?? drop ?? pickup;
    const key = anchor ? `${activeField}:${anchor.latitude},${anchor.longitude}` : activeField;
    // Only once per entry into pin mode, so the rider's own panning is never yanked back.
    if (seededPinFor.current === key) return;
    seededPinFor.current = key;

    if (anchor) {
      setPinAddress(anchor);
      setPinOutOfArea(false);
      mapRef.current?.animateToRegion(
        {
          latitude: anchor.latitude,
          longitude: anchor.longitude,
          latitudeDelta: PIN_ZOOM_DELTA,
          longitudeDelta: PIN_ZOOM_DELTA,
        },
        350
      );
      return;
    }
    // Nothing chosen yet: read back whatever the camera is already over.
    mapRef.current?.getCamera().then((camera) => {
      if (!camera?.center) return;
      onRegionSettled({
        latitude: camera.center.latitude,
        longitude: camera.center.longitude,
        latitudeDelta: 0,
        longitudeDelta: 0,
      });
    });
  }, [collapsed, activeField, drop, pickup, onRegionSettled]);

  const searching = query.trim().length >= 3;

  const rows = useMemo<Row[]>(() => {
    if (searching) {
      const found: Row[] = suggestions.map((s, i) => ({
        type: "suggestion" as const,
        key: `${s.label}-${s.lat}-${s.lng}-${i}`,
        suggestion: s,
      }));
      // Kept below real results too: OSM coverage of Delhi residential addresses is patchy,
      // so "none of these is my building" needs an escape hatch, not a dead end.
      found.push({ type: "map", key: "set-on-map" });
      return found;
    }

    const base: Row[] = [];
    if (activeField === "pickup" && locationGranted) {
      base.push({ type: "current", key: "current-location" });
    }
    recents.forEach((a, i) =>
      base.push({ type: "recent", key: `recent-${a.latitude}-${a.longitude}-${i}`, address: a })
    );
    base.push({ type: "map", key: "set-on-map" });
    if (savedLocations.length > 0) {
      base.push({ type: "header", key: "saved-header", title: "Saved places" });
      savedLocations.forEach((l) => base.push({ type: "saved", key: `saved-${l.id}`, location: l }));
    }
    return base;
  }, [searching, suggestions, recents, savedLocations, activeField, locationGranted]);

  const renderRow = ({ item }: { item: Row }) => {
    switch (item.type) {
      case "header":
        return <Text style={styles.sectionLabel}>{item.title}</Text>;
      case "map":
        return (
          <AddressSuggestionRow
            label="Set location on map"
            description="Drop a pin on the exact spot"
            icon="map-outline"
            action
            onPress={openMapPicker}
          />
        );
      case "current":
        return (
          <AddressSuggestionRow
            label={locatingPickup ? "Getting your location…" : "Use current location"}
            icon="locate"
            action
            onPress={useCurrentLocation}
          />
        );
      case "recent":
        return (
          <AddressSuggestionRow
            label={item.address.label}
            description={item.address.address === item.address.label ? undefined : item.address.address}
            icon="time-outline"
            divider
            onPress={() => choose(item.address)}
          />
        );
      case "saved":
        return (
          <AddressSuggestionRow
            label={item.location.label}
            description={item.location.address}
            icon="bookmark-outline"
            divider
            onPress={() =>
              choose({
                label: item.location.label,
                address: item.location.address,
                latitude: item.location.latitude,
                longitude: item.location.longitude,
              })
            }
          />
        );
      case "suggestion": {
        const s = item.suggestion;
        return (
          <AddressSuggestionRow
            label={s.label}
            description={s.description}
            kind={s.kind}
            divider
            onPress={() =>
              choose({
                label: s.label,
                address: s.description ? `${s.label}, ${s.description}` : s.label,
                latitude: s.lat,
                longitude: s.lng,
              })
            }
          />
        );
      }
    }
  };

  const isPlacingDrop = activeField === "drop";
  const pinTitle = isPlacingDrop ? "Set your destination" : "Set your pickup";
  const pinConfirmLabel = isPlacingDrop ? "Confirm destination" : "Confirm pickup";
  const pinAddLabel = isPlacingDrop ? "Add destination" : "Add pickup";

  const pickupDisplay = pickupQuery || pickup?.address || "";
  const dropDisplay = dropQuery || drop?.address || "";

  return (
    <View style={styles.screen}>
      <Animated.View style={[styles.mapWrap, { height: mapHeight }]}>
        <MapView
          ref={mapRef}
          style={StyleSheet.absoluteFill}
          scrollEnabled
          zoomEnabled
          showsUserLocation={locationGranted}
          // initialRegion, not region: a controlled region snaps the camera back mid-gesture,
          // which makes the pin impossible to place. Recentring is done imperatively instead.
          initialRegion={{
            latitude: pickup?.latitude ?? MAP_DEFAULT_CENTER.latitude,
            longitude: pickup?.longitude ?? MAP_DEFAULT_CENTER.longitude,
            ...(pickup ? { latitudeDelta: 0.03, longitudeDelta: 0.03 } : MAP_DEFAULT_DELTA),
          }}
          onRegionChangeComplete={onRegionSettled}
        >
          {/* The leg being placed is represented by the centre pin, not a marker, so only the
              other one is drawn — two symbols for one point would be ambiguous. */}
          {pickup && !(collapsed && activeField === "pickup") && (
            <Marker coordinate={pickup} anchor={{ x: 0.5, y: 0.5 }}>
              <View style={styles.mapPickupDot} />
            </Marker>
          )}
          {drop && !(collapsed && activeField === "drop") && (
            <Marker coordinate={drop} anchor={{ x: 0.5, y: 0.5 }}>
              <View style={styles.mapDropSquare} />
            </Marker>
          )}
        </MapView>

        {collapsed && (
          <View style={styles.pinLayer} pointerEvents="none">
            <View style={styles.pin}>
              <View style={[styles.pinHead, pinOutOfArea && styles.pinHeadInvalid]} />
              <View style={styles.pinStem} />
            </View>
          </View>
        )}
        <SafeAreaView edges={["top"]} style={styles.mapOverlay} pointerEvents="box-none">
          {collapsed && (
          <Pressable
            style={styles.backButton}
            onPress={() => navigation.goBack()}
            hitSlop={8}
            accessibilityRole="button"
            accessibilityLabel="Go back"
          >
            <Ionicons name="arrow-back" size={20} color={colors.ink} />
          </Pressable>
          )}
        </SafeAreaView>
      </Animated.View>

      {/* Bottom inset lives on the sheet rather than a SafeAreaView so the map behind it can
          still run to the screen edge. */}
      <View style={[styles.sheet, { paddingBottom: insets.bottom }]}>
        {/* Drag zone only — the results list below keeps its own scrolling. */}
        <View style={styles.dragHeader} {...panResponder.panHandlers}>
          <View style={styles.sheetHandle} />
          {/* Expanded, the map is a 96px strip and its overlay back button is squeezed against
              the status bar — so the control lives in the sheet instead, where the rider's
              attention already is. Absolutely positioned to keep the title centred. */}
          {!collapsed && (
            <Pressable
              style={styles.sheetBackButton}
              onPress={() => navigation.goBack()}
              hitSlop={10}
              accessibilityRole="button"
              accessibilityLabel="Go back"
            >
              <Ionicons name="arrow-back" size={22} color={colors.ink} />
            </Pressable>
          )}
          <Animated.Text
            style={[
              styles.title,
              collapsed && styles.titleCollapsed,
              {
                opacity: headingAnim,
                transform: [
                  {
                    translateY: headingAnim.interpolate({
                      inputRange: [0, 1],
                      outputRange: [HEADING_RISE, 0],
                    }),
                  },
                ],
              },
            ]}
          >
            {collapsed ? pinTitle : mode === "edit" ? "Edit trip" : "Plan your trip"}
          </Animated.Text>
          {collapsed && <Text style={styles.subtitle}>Drag map to move pin</Text>}
        </View>

        {collapsed && <View style={styles.headerSeparator} />}

        {collapsed ? (
          // The map is the working surface now: the rider drags it under the fixed centre pin,
          // and this reads back what the pin is currently over.
          <View style={styles.collapsedCard}>
            {/* Tapping the readout is the way back to typing: the rider has decided the pin
                isn't how they want to answer this, so raise the sheet and focus the field. */}
            <Pressable
              style={({ pressed }) => [styles.pinAddressRow, pressed && styles.pinAddressRowPressed]}
              onPress={returnToTyping}
              accessibilityRole="button"
              accessibilityLabel="Search for an address instead"
            >
              <View style={[styles.pinAddressIcon, pinOutOfArea && styles.pinAddressIconInvalid]}>
                <Ionicons
                  name={pinOutOfArea ? "alert-circle-outline" : "location"}
                  size={18}
                  color={pinOutOfArea ? colors.danger : colors.ink}
                />
              </View>
              <View style={styles.pinAddressText}>
                {pinResolving ? (
                  <Text style={styles.pinAddressMuted}>Finding this place…</Text>
                ) : pinOutOfArea ? (
                  <>
                    <Text style={styles.pinAddressInvalid}>Outside the service area</Text>
                    <Text style={styles.pinAddressMuted}>Move the pin closer to Delhi.</Text>
                  </>
                ) : pinAddress ? (
                  <Text style={styles.pinAddressLabel} numberOfLines={2}>
                    {pinAddress.address}
                  </Text>
                ) : (
                  <Text style={styles.pinAddressMuted}>Drag the map to choose a place</Text>
                )}
              </View>
              <Ionicons name="search" size={16} color={colors.inkMuted} />
            </Pressable>

            <PrimaryButton
              label={pinAddress ? pinConfirmLabel : pinAddLabel}
              onPress={() => pinAddress && choose(pinAddress)}
              disabled={!pinAddress || pinResolving || pinOutOfArea}
            />
          </View>
        ) : (
          <View style={styles.fieldsCard}>
            <View style={styles.rail}>
              <View style={styles.pickupDot} />
              <View style={styles.railLine} />
              <View style={styles.dropSquare} />
            </View>
            <View style={styles.fields}>
              <SearchField
                ref={pickupRef}
                value={pickupDisplay}
                onChangeText={setPickupQuery}
                placeholder={locatingPickup ? "Finding you…" : "Pickup location"}
                focused={activeField === "pickup"}
                loading={activeField === "pickup" && loading}
                onFocus={() => setActiveField("pickup")}
                onClear={() => {
                  setPickupQuery("");
                  setPickup(null);
                }}
              />
              <SearchField
                ref={dropRef}
                value={dropDisplay}
                onChangeText={setDropQuery}
                placeholder="Where to?"
                focused={activeField === "drop"}
                loading={activeField === "drop" && loading}
                onFocus={() => setActiveField("drop")}
                onClear={() => {
                  setDropQuery("");
                  setDrop(null);
                }}
              />
            </View>
          </View>
        )}

        {!collapsed && (
        <FlatList
          data={rows}
          keyExtractor={(item) => item.key}
          renderItem={renderRow}
          // Without this a tap lands on the keyboard dismiss instead of the row, and the rider
          // has to tap every result twice.
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="on-drag"
          contentContainerStyle={rows.length === 0 ? styles.emptyContent : styles.listContent}
          ListEmptyComponent={
            <EmptyState searching={searching} loading={loading} error={error} query={query} />
          }
        />
        )}
      </View>
    </View>
  );
}

function EmptyState({
  searching,
  loading,
  error,
  query,
}: {
  searching: boolean;
  loading: boolean;
  error: string | null;
  query: string;
}) {
  if (error) {
    return (
      <View style={styles.empty}>
        <Ionicons name="cloud-offline-outline" size={28} color={colors.inkMuted} />
        <Text style={styles.emptyTitle}>Address search is unavailable</Text>
        <Text style={styles.emptyBody}>Check your connection, then keep typing to try again.</Text>
      </View>
    );
  }
  if (searching && loading) {
    return (
      <View style={styles.empty}>
        <Text style={styles.emptyBody}>Searching…</Text>
      </View>
    );
  }
  if (searching) {
    return (
      <View style={styles.empty}>
        <Ionicons name="search-outline" size={28} color={colors.inkMuted} />
        <Text style={styles.emptyTitle}>No matches for “{query.trim()}”</Text>
        <Text style={styles.emptyBody}>Try a landmark, metro station or neighbourhood nearby.</Text>
      </View>
    );
  }
  return (
    <View style={styles.empty}>
      <Ionicons name="location-outline" size={28} color={colors.inkMuted} />
      <Text style={styles.emptyTitle}>Search for a place</Text>
      <Text style={styles.emptyBody}>Start typing a landmark, area or metro station.</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    // Shows through behind the sheet's rounded top, same as FareCompare.
    backgroundColor: colors.ink,
  },
  mapWrap: {
    width: "100%",
  },
  mapOverlay: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    paddingHorizontal: spacing.md,
    paddingTop: spacing.xs,
  },
  mapPickupDot: {
    width: 14,
    height: 14,
    borderRadius: 7,
    backgroundColor: colors.accent,
    borderWidth: 3,
    borderColor: colors.surface,
  },
  mapDropSquare: {
    width: 14,
    height: 14,
    borderRadius: 3,
    backgroundColor: colors.ink,
    borderWidth: 3,
    borderColor: colors.surface,
  },
  sheet: {
    flex: 1,
    backgroundColor: colors.bg,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    marginTop: -28,
  },
  dragHeader: {
    minHeight: DRAG_HEADER_HEIGHT,
    alignItems: "center",
    justifyContent: "center",
    gap: 4,
    paddingTop: 8,
    // Breathing room under the subtitle before the separator.
    paddingBottom: 14,
  },
  sheetHandle: {
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: colors.border,
  },
  backButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: colors.surface,
    alignItems: "center",
    justifyContent: "center",
    shadowColor: colors.ink,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 6,
    elevation: 3,
  },
  sheetBackButton: {
    position: "absolute",
    left: spacing.md,
    // Aligns with the title rather than the handle above it.
    bottom: 8,
    width: 36,
    height: 36,
    alignItems: "center",
    justifyContent: "center",
  },
  title: {
    textAlign: "center",
    marginTop: 4,
    fontSize: 17,
    fontWeight: "700",
    color: colors.ink,
    letterSpacing: -0.2,
  },
  fieldsCard: {
    flexDirection: "row",
    gap: 12,
    marginHorizontal: spacing.md,
    marginTop: spacing.sm,
    marginBottom: spacing.xs,
  },
  collapsedCard: {
    flex: 1,
    marginHorizontal: spacing.md,
    marginTop: 8,
    // Bottom-aligned as one group: the button sits near the foot of the sheet, with the
    // address readout directly above it rather than stranded at the top.
    justifyContent: "flex-end",
    paddingBottom: 8,
  },
  // Collapsed, the title sits directly under the handle with no fields beneath it to give it
  // room, so it needs its own breathing space.
  titleCollapsed: {
    marginTop: 12,
  },
  subtitle: {
    fontSize: 13,
    color: colors.inkMuted,
    marginTop: 2,
  },
  headerSeparator: {
    height: 1,
    backgroundColor: colors.border,
  },
  // Fixed centre pin. Ignores touches so drags reach the map underneath.
  pinLayer: {
    ...StyleSheet.absoluteFillObject,
    alignItems: "center",
    justifyContent: "center",
  },
  pin: {
    alignItems: "center",
    // Lifts the pin so its tip, not its centre, marks the point under it. Grows with the head
    // so the tip stays on the same coordinate.
    marginBottom: 32,
  },
  pinHead: {
    width: 30,
    height: 30,
    borderRadius: 15,
    backgroundColor: colors.ink,
    borderWidth: 5,
    borderColor: colors.surface,
    shadowColor: colors.ink,
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.3,
    shadowRadius: 5,
    elevation: 5,
  },
  pinHeadInvalid: {
    backgroundColor: colors.danger,
  },
  pinStem: {
    width: 2.5,
    height: 16,
    backgroundColor: colors.ink,
  },
  pinAddressRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    padding: 10,
    marginBottom: 16,
    borderRadius: radii.md,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
  },
  pinAddressRowPressed: {
    backgroundColor: colors.surfaceSunken,
  },
  pinAddressIcon: {
    width: 36,
    height: 36,
    borderRadius: radii.sm,
    backgroundColor: colors.surfaceSunken,
    alignItems: "center",
    justifyContent: "center",
  },
  pinAddressIconInvalid: {
    backgroundColor: colors.dangerBg,
  },
  pinAddressText: {
    flex: 1,
    gap: 2,
  },
  pinAddressLabel: {
    fontSize: 15,
    fontWeight: "600",
    color: colors.ink,
  },
  pinAddressInvalid: {
    fontSize: 15,
    fontWeight: "600",
    color: colors.danger,
  },
  pinAddressMuted: {
    fontSize: 13,
    color: colors.inkMuted,
  },
  rail: {
    width: 12,
    alignItems: "center",
    // Aligns the dot and square to the vertical centre of each 48px field.
    paddingVertical: 20,
  },
  pickupDot: {
    width: 9,
    height: 9,
    borderRadius: 5,
    backgroundColor: colors.accent,
  },
  railLine: {
    flex: 1,
    width: 2,
    backgroundColor: colors.border,
    marginVertical: 5,
  },
  dropSquare: {
    width: 9,
    height: 9,
    borderRadius: 2,
    backgroundColor: colors.ink,
  },
  fields: {
    flex: 1,
    gap: 10,
  },
  listContent: {
    paddingVertical: spacing.xs,
  },
  emptyContent: {
    flexGrow: 1,
  },
  sectionLabel: {
    fontSize: 12,
    fontWeight: "700",
    color: colors.inkMuted,
    textTransform: "uppercase",
    letterSpacing: 0.6,
    paddingHorizontal: spacing.md,
    paddingTop: spacing.sm,
    paddingBottom: spacing.xs,
  },
  empty: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.xl,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: colors.ink,
    textAlign: "center",
  },
  emptyBody: {
    fontSize: 14,
    color: colors.inkMuted,
    textAlign: "center",
    lineHeight: 20,
  },
});
