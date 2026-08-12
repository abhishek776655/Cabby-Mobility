import React from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors, radii } from "@/constants/theme";
import { getVehicleVisual } from "./vehicleVisuals";
import type { VehicleQuote } from "@/api/types";

interface Props {
  quote: VehicleQuote;
  currency: string;
  durationMinutes: number;
  selected: boolean;
  onPress: () => void;
}

export function VehicleOptionCard({ quote, currency, durationMinutes, selected, onPress }: Props) {
  const visual = getVehicleVisual(quote.vehicleType);
  const hasSurge = quote.breakdown.surgeMultiplier > 1;

  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.card,
        selected && styles.cardSelected,
        pressed && !selected && styles.cardPressed,
      ]}
    >
      <View style={[styles.iconWrap, selected && styles.iconWrapSelected]}>
        <Ionicons name={visual.icon} size={30} color={colors.ink} />
      </View>

      <View style={styles.body}>
        <View style={styles.nameRow}>
          <Text style={styles.name}>{visual.label}</Text>
          <Text style={styles.eta}>{durationMinutes} min</Text>
        </View>
        <Text style={styles.blurb} numberOfLines={1}>
          {hasSurge ? `${quote.breakdown.surgeMultiplier}x surge · ${visual.blurb}` : visual.blurb}
        </Text>
      </View>

      <View style={styles.priceWrap}>
        <Text style={styles.price}>
          {currency === "INR" ? "₹" : `${currency} `}{(quote.breakdown.total / 100).toFixed(0)}
        </Text>
        {hasSurge && <Text style={styles.surgeTag}>Surge</Text>}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingVertical: 14,
    paddingHorizontal: 14,
    borderRadius: radii.lg,
    borderWidth: 1.5,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  cardPressed: {
    backgroundColor: colors.surfaceSunken,
  },
  cardSelected: {
    borderColor: colors.ink,
    backgroundColor: colors.surfaceSunken,
  },
  iconWrap: {
    width: 52,
    height: 52,
    borderRadius: radii.md,
    backgroundColor: colors.surfaceSunken,
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
  iconWrapSelected: {
    backgroundColor: colors.surface,
  },
  body: {
    flex: 1,
    gap: 3,
  },
  nameRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  name: {
    fontSize: 16,
    fontWeight: "700",
    color: colors.ink,
  },
  eta: {
    fontSize: 13,
    fontWeight: "600",
    color: colors.inkMuted,
  },
  blurb: {
    fontSize: 13,
    color: colors.inkMuted,
  },
  priceWrap: {
    alignItems: "flex-end",
    gap: 2,
    marginLeft: 4,
  },
  price: {
    fontSize: 17,
    fontWeight: "800",
    color: colors.ink,
  },
  surgeTag: {
    fontSize: 11,
    fontWeight: "700",
    color: colors.warning,
  },
});
