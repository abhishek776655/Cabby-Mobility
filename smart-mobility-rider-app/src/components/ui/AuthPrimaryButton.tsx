import React from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text } from "react-native";
import { authColors } from "@/constants/authTheme";

interface Props {
  label: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
}

export function AuthPrimaryButton({ label, onPress, loading, disabled }: Props) {
  const isDisabled = disabled || loading;

  return (
    <Pressable
      onPress={onPress}
      disabled={isDisabled}
      style={({ pressed }) => [
        styles.button,
        isDisabled && styles.buttonDisabled,
        pressed && !isDisabled && styles.buttonPressed,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={authColors.primaryText} />
      ) : (
        <Text style={styles.label}>{label}</Text>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    height: 52,
    borderRadius: 14,
    backgroundColor: authColors.primary,
    alignItems: "center",
    justifyContent: "center",
    shadowColor: authColors.primary,
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.18,
    shadowRadius: 12,
    elevation: 3,
  },
  buttonPressed: {
    backgroundColor: authColors.primaryPressed,
  },
  buttonDisabled: {
    backgroundColor: authColors.inkFaint,
    shadowOpacity: 0,
    elevation: 0,
  },
  label: {
    color: authColors.primaryText,
    fontSize: 16,
    fontWeight: "700",
    letterSpacing: 0.2,
  },
});
