import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { colors } from "@/constants/theme";

interface Props {
  pickupLabel: string;
  dropLabel: string;
}

const ICON_COL_WIDTH = 18;

// The pickup-dot / dashed-line / drop-pin glyph — the one visual motif every ride-hailing
// app uses to make "this trip, these two points" scannable at a glance. Each pointer sits in
// a fixed-width left column so it lines up exactly with its own label, and a divider line
// (inset to start under the text, not the pointer) separates the two stops.
export function TripLine({ pickupLabel, dropLabel }: Props) {
  return (
    <View>
      <View style={styles.row}>
        <View style={styles.iconCol}>
          <View style={styles.pickupDot} />
        </View>
        <Text style={styles.label} numberOfLines={1}>
          {pickupLabel}
        </Text>
      </View>

      <View style={styles.connectorRow}>
        <View style={styles.iconCol}>
          <View style={styles.dash} />
        </View>
        <View style={styles.divider} />
      </View>

      <View style={styles.row}>
        <View style={styles.iconCol}>
          <View style={styles.dropSquare} />
        </View>
        <Text style={styles.label} numberOfLines={1}>
          {dropLabel}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  connectorRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    height: 26,
  },
  iconCol: {
    width: ICON_COL_WIDTH,
    alignItems: "center",
    justifyContent: "center",
  },
  pickupDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: colors.accent,
  },
  dash: {
    width: 2,
    height: 22,
    backgroundColor: colors.border,
  },
  dropSquare: {
    width: 10,
    height: 10,
    borderRadius: 2,
    backgroundColor: colors.ink,
  },
  divider: {
    flex: 1,
    height: 1,
    backgroundColor: colors.border,
  },
  label: {
    flex: 1,
    fontSize: 15,
    fontWeight: "600",
    color: colors.ink,
  },
});
