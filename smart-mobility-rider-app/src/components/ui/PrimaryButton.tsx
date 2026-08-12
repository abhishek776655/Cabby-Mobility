import React from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text } from "react-native";
import { colors } from "@/constants/theme";

interface Props {
  label: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  variant?: "primary" | "ghost";
}

export function PrimaryButton({ label, onPress, loading, disabled, variant = "primary" }: Props) {
  const isDisabled = disabled || loading;
  const isGhost = variant === "ghost";

  return (
    <Pressable
      onPress={onPress}
      disabled={isDisabled}
      style={({ pressed }) => [
        styles.button,
        isGhost && styles.buttonGhost,
        isDisabled && (isGhost ? styles.buttonGhostDisabled : styles.buttonDisabled),
        pressed && !isDisabled && (isGhost ? styles.buttonGhostPressed : styles.buttonPressed),
      ]}
    >
      {loading ? (
        <ActivityIndicator color={isGhost ? colors.primary : colors.primaryText} />
      ) : (
        <Text style={[styles.label, isGhost && styles.labelGhost]}>{label}</Text>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    height: 54,
    borderRadius: 16,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
    shadowColor: colors.primary,
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.2,
    shadowRadius: 14,
    elevation: 4,
  },
  buttonPressed: {
    backgroundColor: colors.primaryPressed,
  },
  buttonDisabled: {
    backgroundColor: colors.inkFaint,
    shadowOpacity: 0,
    elevation: 0,
  },
  buttonGhost: {
    backgroundColor: colors.surface,
    borderWidth: 1.5,
    borderColor: colors.border,
    shadowOpacity: 0,
    elevation: 0,
  },
  buttonGhostPressed: {
    backgroundColor: colors.surfaceSunken,
  },
  buttonGhostDisabled: {
    opacity: 0.5,
  },
  label: {
    color: colors.primaryText,
    fontSize: 16,
    fontWeight: "700",
    letterSpacing: 0.2,
  },
  labelGhost: {
    color: colors.ink,
  },
});
