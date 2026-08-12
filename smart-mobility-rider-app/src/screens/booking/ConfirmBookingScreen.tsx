import React, { useEffect, useState } from "react";
import { StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { BookingStackParamList } from "@/navigation/types";
import { createRide } from "@/api/rides";
import { getMe } from "@/api/riders";
import { useAuthStore } from "@/store/authStore";
import { useRideStore } from "@/store/rideStore";
import { toApiError, type ApiError } from "@/api/client";
import type { PreferredPaymentMethod } from "@/api/types";
import { TripLine } from "@/components/booking/TripLine";
import { getVehicleVisual } from "@/components/booking/vehicleVisuals";
import { getPaymentVisual } from "@/components/booking/paymentVisuals";
import { PrimaryButton } from "@/components/ui/PrimaryButton";
import { colors, radii, spacing } from "@/constants/theme";

type Props = NativeStackScreenProps<BookingStackParamList, "ConfirmBooking">;

export function ConfirmBookingScreen({ route, navigation }: Props) {
  const { pickupLat, pickupLng, dropLat, dropLng, pickupLocation, dropLocation, quote } =
    route.params;
  const userId = useAuthStore((s) => s.userId);
  const setActiveRide = useRideStore((s) => s.setActiveRide);
  const visual = getVehicleVisual(quote.vehicleType);

  const [paymentMethod, setPaymentMethod] = useState<PreferredPaymentMethod | null>(
    route.params.paymentMethod ?? null
  );
  const [booking, setBooking] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(() => {
    // Picked on FareCompare already — no need to hit the profile endpoint again.
    if (route.params.paymentMethod) return;
    getMe()
      .then((profile) => setPaymentMethod(profile.preferredPaymentMethod))
      .catch(() => setPaymentMethod("CASH"));
  }, [route.params.paymentMethod]);

  const onConfirm = async () => {
    if (!userId) return;
    setBooking(true);
    setError(null);
    try {
      const ride = await createRide({
        riderUserId: userId,
        pickupLocation,
        dropLocation,
        pickupLatitude: pickupLat,
        pickupLongitude: pickupLng,
        dropLatitude: dropLat,
        dropLongitude: dropLng,
        vehicleType: quote.vehicleType,
      });
      setActiveRide(ride.rideId, ride.status);
      navigation.replace("SearchingDriver", { rideId: ride.rideId });
    } catch (e) {
      setError(toApiError(e as any));
    } finally {
      setBooking(false);
    }
  };

  return (
    <SafeAreaView style={styles.safeArea} edges={["bottom"]}>
      <View style={styles.content}>
        <View style={styles.card}>
          <TripLine pickupLabel={pickupLocation} dropLabel={dropLocation} />
        </View>

        <View style={styles.card}>
          <View style={styles.vehicleRow}>
            <View style={styles.vehicleIconWrap}>
              <Ionicons name={visual.icon} size={24} color={colors.ink} />
            </View>
            <View style={styles.vehicleText}>
              <Text style={styles.vehicleName}>{visual.label}</Text>
              {quote.breakdown.surgeMultiplier > 1 && (
                <Text style={styles.surgeText}>{quote.breakdown.surgeMultiplier}x surge applied</Text>
              )}
            </View>
            <Text style={styles.price}>₹{(quote.breakdown.total / 100).toFixed(2)}</Text>
          </View>
        </View>

        <View style={styles.card}>
          <View style={styles.paymentRow}>
            <Ionicons
              name={paymentMethod ? getPaymentVisual(paymentMethod).icon : "ellipsis-horizontal-outline"}
              size={20}
              color={colors.inkMuted}
            />
            <Text style={styles.paymentText}>
              Paying with {paymentMethod ? getPaymentVisual(paymentMethod).label : "…"}
            </Text>
          </View>
        </View>

        {error && <Text style={styles.errorText}>{error.message}</Text>}
      </View>

      <View style={styles.footer}>
        <PrimaryButton
          label={booking ? "Booking…" : `Confirm · ₹${(quote.breakdown.total / 100).toFixed(0)}`}
          loading={booking}
          disabled={booking || !paymentMethod}
          onPress={onConfirm}
        />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  content: {
    flex: 1,
    padding: spacing.md,
    gap: spacing.sm,
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.sm,
  },
  vehicleRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  vehicleIconWrap: {
    width: 44,
    height: 44,
    borderRadius: radii.sm,
    backgroundColor: colors.surfaceSunken,
    alignItems: "center",
    justifyContent: "center",
  },
  vehicleText: {
    flex: 1,
  },
  vehicleName: {
    fontSize: 16,
    fontWeight: "700",
    color: colors.ink,
  },
  surgeText: {
    fontSize: 12,
    color: colors.warning,
    marginTop: 2,
  },
  price: {
    fontSize: 20,
    fontWeight: "800",
    color: colors.ink,
  },
  paymentRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  paymentText: {
    fontSize: 15,
    fontWeight: "600",
    color: colors.ink,
  },
  errorText: {
    color: colors.danger,
    fontSize: 14,
    textAlign: "center",
  },
  footer: {
    padding: spacing.md,
  },
});
