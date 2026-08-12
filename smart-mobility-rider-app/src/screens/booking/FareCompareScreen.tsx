import React, { useEffect, useRef, useState } from "react";
import {
  Animated,
  FlatList,
  Modal,
  PanResponder,
  Pressable,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { BookingStackParamList } from "@/navigation/types";
import { quoteAll } from "@/api/fares";
import { reverseGeocode } from "@/api/geocode";
import { getMe, updatePreferences } from "@/api/riders";
import { useRideStore } from "@/store/rideStore";
import { useAddressPickerStore } from "@/store/addressPickerStore";
import { toApiError, type ApiError } from "@/api/client";
import type { PreferredPaymentMethod, QuoteAllResponse, VehicleQuote } from "@/api/types";
import { RouteMapPreview } from "@/components/booking/RouteMapPreview";
import { VehicleOptionCard } from "@/components/booking/VehicleOptionCard";
import { PrimaryButton } from "@/components/ui/PrimaryButton";
import { getVehicleVisual } from "@/components/booking/vehicleVisuals";
import { PAYMENT_METHODS, getPaymentVisual } from "@/components/booking/paymentVisuals";
import { colors, radii, spacing } from "@/constants/theme";

type Props = NativeStackScreenProps<BookingStackParamList, "FareCompare">;

interface Point {
  label: string;
  address: string;
  latitude: number;
  longitude: number;
}

const MAP_HEIGHT_DEFAULT = 320;
const MAP_HEIGHT_MIN = 110;
const ZOOM_RANGE = MAP_HEIGHT_DEFAULT - MAP_HEIGHT_MIN;
// Payment row + book button live outside the draggable sheet entirely, pinned to the screen
// bottom at a fixed height — they must never move or resize as the map/sheet is dragged.
const BOTTOM_BAR_HEIGHT = 178;
// Drag header is given this EXACT height in its style (not padding-derived) so it is
// pixel-fixed regardless of drag state — no flex, no content-based sizing that could drift.
const DRAG_HEADER_HEIGHT = 50;
// VehicleOptionCard's real rendered height — only a first-paint fallback until the actual card
// is measured on-device below (fonts/scale can shift this from the paddingVertical-14*2 +
// 52px-icon estimate); the peek floor should track the real content, not a guess.
const VEHICLE_CARD_HEIGHT_ESTIMATE = 14 * 2 + 52;
// The list's own vertical padding around that single card. The list is top-aligned with
// paddingTop 0 and no bottom padding, so this is 0 — any value above the real padding just
// becomes dead space between the card and the fixed payment bar at full peek.
const LIST_PEEK_VERTICAL_PADDING = 0;
// The sheet's rounded top slides this far UP over the map (style marginTop: -SHEET_TOP_OVERLAP),
// so the sheet box is this much taller than the gap the map leaves it...
const SHEET_TOP_OVERLAP = 28;
// ...and this much of that box is eaten by the sheet's own top padding, above the drag header.
// Both must be in the peek budget below: the overlap is extra usable height the naive
// (window - map - bottomBar) math doesn't see, and it was showing up as leftover space under
// the card at full peek.
const SHEET_PADDING_TOP = spacing.xs;

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

/**
 * Condenses an address to what actually identifies it in a two-line header bubble.
 *
 * The house number or block is the most specific thing a residential address carries, so it
 * leads when present ("A-115, Block-A, Street Number 2" -> "A-115, Block-A"). Named places
 * have no such token and fall back to their first words ("Qutub Minar Complex, Baba
 * Shrichand Marg" -> "Qutub Minar").
 */
export function shortName(address: string): string {
  const parts = address
    .split(",")
    .map((p) => p.trim())
    .filter(Boolean);
  if (parts.length === 0) return address;

  const hasIdentifier = (part: string) => /\d/.test(part) || /\bblock\b/i.test(part);
  const identifying = parts.filter(hasIdentifier).slice(0, 2);
  if (identifying.length > 0) return identifying.join(", ");

  // No number or block: first segment, trimmed to its first two words so a long street name
  // doesn't dominate the pill.
  return parts[0].split(/\s+/).slice(0, 2).join(" ");
}

export function FareCompareScreen({ route, navigation }: Props) {
  const setFareQuote = useRideStore((s) => s.setFareQuote);
  const { height: windowHeight } = useWindowDimensions();

  // The trip being priced — starts from the route params, but the destination bubble lets the
  // rider edit it in place (no navigation), so it has to live in local state, not stay tied to
  // route.params.
  const [tripPickup, setTripPickup] = useState<Point>({
    label: "Pickup",
    address: route.params.pickupLocation,
    latitude: route.params.pickupLat,
    longitude: route.params.pickupLng,
  });
  const [tripDrop, setTripDrop] = useState<Point>({
    label: "Drop",
    address: route.params.dropLocation,
    latitude: route.params.dropLat,
    longitude: route.params.dropLng,
  });

  // BOTTOM_BAR_HEIGHT is only a fallback for the first paint — the sheet is a flex:1 sibling,
  // so it always renders at exactly (window - map - actual bottom bar) regardless of this
  // guess. If the guess is off, the drag limit stops the map short of that real boundary and
  // leaves dead space between the last card and the bottom bar. Measuring the real height
  // keeps the limit (and that space) exact.
  const [bottomBarHeight, setBottomBarHeight] = useState(BOTTOM_BAR_HEIGHT);
  // Same deal for the card: VEHICLE_CARD_HEIGHT_ESTIMATE is only a first-paint fallback — the
  // rendered VehicleOptionCard is measured below and that real value is used from then on, so
  // the peek floor always matches the card exactly regardless of font scale/device, instead of
  // being pinned to a hand-picked constant.
  const [cardHeight, setCardHeight] = useState(VEHICLE_CARD_HEIGHT_ESTIMATE);
  const cardHeightMeasured = useRef(false);
  const sheetPeekContentHeight =
    DRAG_HEADER_HEIGHT +
    cardHeight +
    LIST_PEEK_VERTICAL_PADDING +
    SHEET_PADDING_TOP -
    SHEET_TOP_OVERLAP;
  const mapHeightMax = Math.max(
    MAP_HEIGHT_DEFAULT,
    windowHeight - bottomBarHeight - sheetPeekContentHeight
  );

  const [quote, setQuote] = useState<QuoteAllResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [selected, setSelected] = useState<VehicleQuote | null>(null);
  const [zoomProgress, setZoomProgress] = useState(0);
  const [isPeek, setIsPeek] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<PreferredPaymentMethod | null>(null);
  const [paymentPickerOpen, setPaymentPickerOpen] = useState(false);

  const pickerField = useAddressPickerStore((s) => s.field);
  const pickerResult = useAddressPickerStore((s) => s.result);
  const openPicker = useAddressPickerStore((s) => s.openFor);
  const consumePicker = useAddressPickerStore((s) => s.consume);

  // Editing a leg opens the same trip planner the booking flow starts with, in "edit" mode.
  // This screen used to carry its own inline copy of that UI — two implementations of one
  // job, which drifted apart as the planner gained recents, map-pin fallback and debouncing.
  const openSearch = (field: "pickup" | "drop") => {
    openPicker(field);
    navigation.navigate("AddressSearch", {
      field,
      mode: "edit",
      pickup: tripPickup,
      drop: tripDrop,
    });
  };

  // "Your current location" is a status, not a place name — useless in a bubble that has to
  // tell the rider which trip is being priced. Resolve it to a real name once.
  const [pickupResolvedName, setPickupResolvedName] = useState<string | null>(null);
  const isLivePickup = /current location/i.test(tripPickup.address);

  useEffect(() => {
    if (!isLivePickup) {
      setPickupResolvedName(null);
      return;
    }
    let cancelled = false;
    reverseGeocode(tripPickup.latitude, tripPickup.longitude)
      .then((result) => {
        if (!cancelled && result) setPickupResolvedName(result.label);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [isLivePickup, tripPickup.latitude, tripPickup.longitude]);

  const pickupShortName = shortName(pickupResolvedName ?? tripPickup.address);

  // The planner hands the resolved leg back through the store; apply it to the live trip,
  // which re-runs the quote below.
  useEffect(() => {
    if (!pickerResult || !pickerField) return;
    if (pickerField === "pickup") {
      setTripPickup(pickerResult);
    } else {
      setTripDrop(pickerResult);
    }
    consumePicker();
  }, [pickerResult, pickerField, consumePicker]);

  const mapHeight = useRef(new Animated.Value(MAP_HEIGHT_DEFAULT)).current;
  const dragStartHeight = useRef(MAP_HEIGHT_DEFAULT);
  const currentHeight = useRef(MAP_HEIGHT_DEFAULT);
  // PanResponder.create() below runs once (useRef initializer), so its callbacks close over
  // whatever mapHeightMax was on that first render. bottomBarHeight is only known after the
  // bottom bar's first onLayout, so mapHeightMax changes after mount — without this ref the
  // drag stayed clamped to the stale first-render value and never reached the real peek
  // threshold, so isPeek (computed from the up-to-date mapHeightMax below) never flipped true.
  const mapHeightMaxRef = useRef(mapHeightMax);

  useEffect(() => {
    mapHeightMaxRef.current = mapHeightMax;
  }, [mapHeightMax]);

  useEffect(() => {
    const id = mapHeight.addListener(({ value }) => {
      currentHeight.current = value;
      setZoomProgress((MAP_HEIGHT_DEFAULT - value) / ZOOM_RANGE);
      setIsPeek(value >= mapHeightMax - 4);
    });
    return () => mapHeight.removeListener(id);
  }, [mapHeight, mapHeightMax]);

  const panResponder = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_evt, gesture) => Math.abs(gesture.dy) > 4,
      onPanResponderGrant: () => {
        dragStartHeight.current = currentHeight.current;
      },
      onPanResponderMove: (_evt, gesture) => {
        // gesture.dy is negative when dragging the finger up — subtracting it would grow the
        // map, the opposite of "drag up to shrink the map / zoom in". Add it instead.
        const next = clamp(dragStartHeight.current + gesture.dy, MAP_HEIGHT_MIN, mapHeightMaxRef.current);
        mapHeight.setValue(next);
      },
      onPanResponderRelease: (_evt, gesture) => {
        const max = mapHeightMaxRef.current;
        const snapPoints = [MAP_HEIGHT_MIN, MAP_HEIGHT_DEFAULT, max];
        let target = snapPoints.reduce((closest, point) =>
          Math.abs(point - currentHeight.current) < Math.abs(closest - currentHeight.current) ? point : closest
        );
        if (gesture.vy < -0.6) target = MAP_HEIGHT_MIN;
        else if (gesture.vy > 0.6) target = max;
        Animated.spring(mapHeight, {
          toValue: target,
          useNativeDriver: false,
          bounciness: 4,
        }).start();
      },
    })
  ).current;

  const fetchQuote = () => {
    setLoading(true);
    setError(null);
    quoteAll({
      pickupLat: tripPickup.latitude,
      pickupLng: tripPickup.longitude,
      dropLat: tripDrop.latitude,
      dropLng: tripDrop.longitude,
    })
      .then((result) => {
        setQuote(result);
        setFareQuote(result);
        setSelected(result.quotes[0] ?? null);
      })
      .catch((e) => setError(toApiError(e)))
      .finally(() => setLoading(false));
  };

  useEffect(fetchQuote, [tripPickup.latitude, tripPickup.longitude, tripDrop.latitude, tripDrop.longitude]);

  const onConfirm = () => {
    if (!selected) return;
    navigation.navigate("ConfirmBooking", {
      pickupLat: tripPickup.latitude,
      pickupLng: tripPickup.longitude,
      dropLat: tripDrop.latitude,
      dropLng: tripDrop.longitude,
      pickupLocation: tripPickup.address,
      dropLocation: tripDrop.address,
      quote: selected,
      paymentMethod: paymentMethod ?? undefined,
    });
  };

  if (error) {
    return (
      <SafeAreaView style={styles.centerScreen} edges={["bottom"]}>
        <Text style={styles.errorText}>
          {error.isCircuitOpen
            ? "Fares are temporarily unavailable. Try again shortly."
            : error.message}
        </Text>
        <PrimaryButton label="Retry" variant="ghost" onPress={fetchQuote} />
      </SafeAreaView>
    );
  }

  const durationMinutes = quote ? Math.max(1, Math.round(quote.durationSeconds / 60)) : 0;
  const selectedVisual = selected ? getVehicleVisual(selected.vehicleType) : null;
  const allQuotes = quote?.quotes ?? [];
  const visibleQuotes = isPeek && selected ? allQuotes.filter((q) => q.vehicleType === selected.vehicleType) : allQuotes;



  return (
    <View style={styles.screen}>
      <Animated.View style={[styles.mapWrap, { height: mapHeight }]}>
        <RouteMapPreview
          pickup={{ latitude: tripPickup.latitude, longitude: tripPickup.longitude }}
          drop={{ latitude: tripDrop.latitude, longitude: tripDrop.longitude }}
          routeCoordinates={quote?.coordinates}
          height="100%"
          rounded={false}
          zoomProgress={zoomProgress}
        />
        <SafeAreaView edges={["top"]} style={styles.mapOverlayRow}>
          <Pressable style={styles.backButton} onPress={() => navigation.goBack()}>
            <Ionicons name="arrow-back" size={24} color={colors.ink} />
          </Pressable>
          <View style={styles.destinationBubbleWrap}>
            <Pressable
              style={styles.destinationBubble}
              onPress={() => openSearch("drop")}
              accessibilityRole="button"
              accessibilityLabel={`Edit trip from ${pickupShortName} to ${tripDrop.address}`}
            >
              {/* Source above destination, mirroring the rail glyph used everywhere else in
                  the flow — the same top-to-bottom reading order as the trip itself. */}
              <View style={styles.bubbleRail}>
                <View style={styles.bubblePickupDot} />
                <View style={styles.bubbleRailLine} />
                <View style={styles.bubbleDropSquare} />
              </View>
              <View style={styles.bubbleLegs}>
                <Text style={styles.bubbleSourceText} numberOfLines={1}>
                  {pickupShortName}
                </Text>
                <Text style={styles.bubbleLegText} numberOfLines={1}>
                  {shortName(tripDrop.address)}
                </Text>
              </View>
            </Pressable>
          </View>
          <View style={styles.backButtonSpacer} />
        </SafeAreaView>
      </Animated.View>

      <View style={styles.sheet}>
        <View style={styles.dragHeader} {...panResponder.panHandlers}>
          <View style={styles.sheetHandle} />
        </View>

        <View style={styles.swapArea}>
          <View style={styles.swapPanel}>
            {/*
              Styles here are deliberately NOT switched on isPeek. Swapping the list container
              (flex:1 vs flexGrow:0) and its alignment (top vs bottom) the instant the peek
              threshold is crossed re-lays-out the whole panel in a single frame, which is what
              read as the sheet "snapping"/resizing mid-drag. Keeping them constant means the
              card sits at a fixed offset under the drag header at every drag position; only the
              row data below it changes, which is invisible because the trimmed rows were already
              past the fold at that sheet height.
            */}
            <FlatList
              data={visibleQuotes}
              keyExtractor={(item) => item.vehicleType}
              style={styles.listOuter}
              contentContainerStyle={styles.list}
              refreshing={loading}
              ItemSeparatorComponent={() => <View style={{ height: 10 }} />}
              renderItem={({ item, index }) => (
                <View
                  onLayout={
                    index === 0
                      ? (e) => {
                        if (cardHeightMeasured.current) return;
                        cardHeightMeasured.current = true;
                        setCardHeight(e.nativeEvent.layout.height);
                      }
                      : undefined
                  }
                >
                  <VehicleOptionCard
                    quote={item}
                    currency={quote?.currency ?? "INR"}
                    durationMinutes={durationMinutes}
                    selected={selected?.vehicleType === item.vehicleType}
                    onPress={() => setSelected(item)}
                  />
                </View>
              )}
            />
          </View>
        </View>
      </View>

      <View style={styles.bottomBar} onLayout={(e) => setBottomBarHeight(e.nativeEvent.layout.height)}>
            <Pressable style={styles.paymentRow} onPress={() => setPaymentPickerOpen(true)}>
              <View style={styles.paymentIconWrap}>
                <Ionicons
                  name={paymentMethod ? getPaymentVisual(paymentMethod).icon : "card-outline"}
                  size={18}
                  color={colors.ink}
                />
              </View>
              <Text style={styles.paymentText}>
                {paymentMethod ? getPaymentVisual(paymentMethod).label : "Choose payment"}
              </Text>
              <Ionicons name="chevron-forward" size={18} color={colors.inkMuted} />
            </Pressable>

            <View style={styles.footer}>
              <PrimaryButton
                label={
                  selected && selectedVisual
                    ? `Book ${selectedVisual.label} · ₹${(selected.breakdown.total / 100).toFixed(0)}`
                    : "Choose a ride"
                }
                onPress={onConfirm}
                disabled={!selected || loading}
              />
            </View>
      </View>

      <Modal visible={paymentPickerOpen} transparent animationType="fade" onRequestClose={() => setPaymentPickerOpen(false)}>
        <Pressable style={styles.modalBackdrop} onPress={() => setPaymentPickerOpen(false)}>
          <Pressable style={styles.modalSheet} onPress={() => { }}>
            <View style={[styles.sheetHandle, styles.modalHandle]} />
            <Text style={styles.modalTitle}>Payment method</Text>
            {PAYMENT_METHODS.map((method) => {
              const active = paymentMethod === method.value;
              return (
                <Pressable
                  key={method.value}
                  style={[styles.paymentOption, active && styles.paymentOptionActive]}
                  onPress={() => onSelectPayment(method.value)}
                >
                  <Ionicons name={method.icon} size={20} color={colors.ink} />
                  <Text style={styles.paymentOptionText}>{method.label}</Text>
                  {active && <Ionicons name="checkmark-circle" size={20} color={colors.primary} />}
                </Pressable>
              );
            })}
          </Pressable>
        </Pressable>
      </Modal>
    </View>
  );

  function onSelectPayment(method: PreferredPaymentMethod) {
    setPaymentMethod(method);
    setPaymentPickerOpen(false);
    updatePreferences(method).catch(() => { });
  }
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: colors.ink,
  },
  centerScreen: {
    flex: 1,
    backgroundColor: colors.bg,
    justifyContent: "center",
    alignItems: "center",
    padding: spacing.md,
    gap: spacing.sm,
  },
  errorText: {
    color: colors.danger,
    fontSize: 15,
    textAlign: "center",
  },
  mapWrap: {
    width: "100%",
  },
  mapOverlayRow: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    flexDirection: "row",
    alignItems: "center",
    marginTop: spacing.xs,
    paddingHorizontal: spacing.md,
    gap: spacing.sm,
  },
  backButton: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: colors.surface,
    alignItems: "center",
    justifyContent: "center",
    shadowColor: colors.ink,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 6,
    elevation: 3,
  },
  // Balances the back button's width so the destination bubble in between is optically
  // centered on the map instead of skewed right.
  backButtonSpacer: {
    width: 52,
  },
  destinationBubbleWrap: {
    flex: 1,
    alignItems: "center",
  },
  destinationBubble: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    // Taller than a single-line pill: it stacks both legs.
    paddingVertical: 10,
    maxWidth: "88%",
    // Fully rounded ends rather than a soft rectangle — the pill reads as a control floating
    // over the map, not as a card sitting on it.
    borderRadius: radii.pill,
    backgroundColor: colors.surface,
    paddingHorizontal: 14,
    shadowColor: colors.ink,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 6,
    elevation: 3,
  },
  bubbleRail: {
    alignItems: "center",
    paddingVertical: 4,
    flexShrink: 0,
  },
  bubblePickupDot: {
    width: 7,
    height: 7,
    borderRadius: 4,
    backgroundColor: colors.accent,
  },
  bubbleRailLine: {
    flex: 1,
    width: 1.5,
    minHeight: 10,
    backgroundColor: colors.border,
    marginVertical: 2,
  },
  bubbleDropSquare: {
    width: 7,
    height: 7,
    borderRadius: 2,
    backgroundColor: colors.ink,
  },
  bubbleLegs: {
    flexShrink: 1,
    gap: 2,
  },
  // Source is context for the destination, not a peer of it — smaller and quieter so the
  // pill has an obvious primary line.
  bubbleSourceText: {
    fontSize: 11,
    fontWeight: "600",
    color: colors.inkMuted,
  },
  bubbleLegText: {
    fontSize: 13,
    fontWeight: "700",
    color: colors.ink,
  },
  sheet: {
    flex: 1,
    backgroundColor: colors.bg,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    marginTop: -SHEET_TOP_OVERLAP,
    paddingTop: SHEET_PADDING_TOP,
  },
  // The fare panel and the edit panel occupy the same box and cross-fade/slide between each
  // other — both absolutely fill this relatively-positioned area.
  swapArea: {
    flex: 1,
    position: "relative",
  },
  swapPanel: {
    ...StyleSheet.absoluteFillObject,
  },
  // Fixed-height, never resized by the drag gesture — payment method + book button always
  // stay put at the screen bottom regardless of how the map/sheet above is dragged.
  bottomBar: {
    backgroundColor: colors.bg,
  },
  // Fixed-height drag strip, mirroring bottomBar — a real hit area pinned above the scrolling
  // list, not just a bare 4px dot that's easy to miss and lets the touch fall through to the
  // list underneath instead of starting a drag.
  // Handle sits near the top of the strip rather than centered — the strip is deliberately tall
  // for a comfortable grab area, and centering the handle in it would push the handle far from
  // the sheet's top edge.
  dragHeader: {
    height: DRAG_HEADER_HEIGHT,
    alignItems: "center",
    justifyContent: "flex-start",
    paddingTop: 5
  },
  sheetHandle: {
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: colors.border,
  },
  listOuter: {
    flex: 1,
  },
  list: {
    paddingHorizontal: spacing.md,
    flexGrow: 1,
    paddingTop: 0,
  },
  footer: {
    padding: spacing.md,
    paddingTop: spacing.xs,
  },
  paymentRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    marginHorizontal: spacing.md,
    marginTop: spacing.sm,
    padding: 12,
    borderRadius: radii.lg,
    borderWidth: 1.5,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  paymentIconWrap: {
    width: 36,
    height: 36,
    borderRadius: radii.sm,
    backgroundColor: colors.surfaceSunken,
    alignItems: "center",
    justifyContent: "center",
  },
  paymentText: {
    flex: 1,
    fontSize: 15,
    fontWeight: "600",
    color: colors.ink,
  },
  modalBackdrop: {
    flex: 1,
    backgroundColor: colors.overlay,
    justifyContent: "flex-end",
  },
  modalSheet: {
    backgroundColor: colors.bg,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    padding: spacing.md,
    paddingBottom: spacing.lg,
  },
  modalHandle: {
    alignSelf: "center",
    marginBottom: spacing.sm,
  },
  modalTitle: {
    fontSize: 17,
    fontWeight: "700",
    color: colors.ink,
    marginBottom: spacing.sm,
    textAlign: "center",
  },
  paymentOption: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    padding: 14,
    borderRadius: radii.md,
    borderWidth: 1.5,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    marginBottom: 8,
  },
  paymentOptionActive: {
    borderColor: colors.ink,
    backgroundColor: colors.surfaceSunken,
  },
  paymentOptionText: {
    flex: 1,
    fontSize: 15,
    fontWeight: "600",
    color: colors.ink,
  },
});
