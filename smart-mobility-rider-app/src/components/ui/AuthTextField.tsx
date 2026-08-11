import React, { useState } from "react";
import { StyleSheet, Text, TextInput, TextInputProps, View } from "react-native";
import { authColors } from "@/constants/authTheme";

interface Props extends TextInputProps {
  label: string;
  error?: boolean;
  rightAccessory?: React.ReactNode;
}

export function AuthTextField({ label, error, rightAccessory, style, ...inputProps }: Props) {
  const [focused, setFocused] = useState(false);

  const borderColor = error
    ? authColors.danger
    : focused
    ? authColors.borderFocus
    : authColors.border;

  return (
    <View style={styles.container}>
      <Text style={styles.label}>{label}</Text>
      <View style={[styles.fieldRow, { borderColor, borderWidth: focused || error ? 1.5 : 1 }]}>
        <TextInput
          {...inputProps}
          style={[styles.input, style]}
          placeholderTextColor={authColors.inkFaint}
          onFocus={(e) => {
            setFocused(true);
            inputProps.onFocus?.(e);
          }}
          onBlur={(e) => {
            setFocused(false);
            inputProps.onBlur?.(e);
          }}
        />
        {rightAccessory}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 6,
  },
  label: {
    fontSize: 13,
    fontWeight: "600",
    color: authColors.inkMuted,
    letterSpacing: 0.2,
  },
  fieldRow: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: authColors.surface,
    borderRadius: 12,
    paddingHorizontal: 14,
  },
  input: {
    flex: 1,
    fontSize: 16,
    color: authColors.ink,
    paddingVertical: 14,
  },
});
