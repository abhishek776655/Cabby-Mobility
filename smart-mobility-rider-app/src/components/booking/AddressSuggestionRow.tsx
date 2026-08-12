import React from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors, radii, spacing } from "@/constants/theme";
import { getAddressKindIcon } from "./addressKindVisuals";

interface Props {
  label: string;
  description?: string;
  kind?: string | null;
  /** Overrides the kind-derived icon — used for "saved place" and "current location" rows. */
  icon?: React.ComponentProps<typeof Ionicons>["name"];
  /** Hairline under the row, so a list of places reads as discrete entries. */
  divider?: boolean;
  /**
   * Renders as an action rather than a place: accent icon and a chevron. Keeps "Set location
   * on map" from looking like one more address in a list of addresses.
   */
  action?: boolean;
  onPress: () => void;
}

export function AddressSuggestionRow({
  label,
  description,
  kind,
  icon,
  divider,
  action,
  onPress,
}: Props) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [styles.row, divider && styles.rowDivided, pressed && styles.rowPressed]}
      accessibilityRole="button"
      accessibilityLabel={description ? `${label}, ${description}` : label}
    >
      <View style={[styles.iconWrap, action && styles.iconWrapAction]}>
        <Ionicons
          name={icon ?? getAddressKindIcon(kind)}
          size={18}
          color={action ? colors.primary : colors.ink}
        />
      </View>
      <View style={styles.text}>
        <Text style={[styles.label, action && styles.labelAction]} numberOfLines={1}>
          {label}
        </Text>
        {!!description && (
          <Text style={styles.description} numberOfLines={1}>
            {description}
          </Text>
        )}
      </View>
      {action && <Ionicons name="chevron-forward" size={16} color={colors.inkMuted} />}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    paddingVertical: 12,
    paddingHorizontal: spacing.md,
  },
  rowDivided: {
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  rowPressed: {
    backgroundColor: colors.surfaceSunken,
  },
  iconWrap: {
    width: 40,
    height: 40,
    borderRadius: radii.pill,
    backgroundColor: colors.surfaceSunken,
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
  iconWrapAction: {
    backgroundColor: colors.primarySoft,
  },
  labelAction: {
    color: colors.primary,
    fontWeight: "700",
  },
  text: {
    flex: 1,
    gap: 2,
  },
  label: {
    fontSize: 15,
    fontWeight: "600",
    color: colors.ink,
  },
  description: {
    fontSize: 13,
    color: colors.inkMuted,
  },
});
