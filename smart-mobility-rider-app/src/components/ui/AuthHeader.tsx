import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { authColors } from "@/constants/authTheme";

export function AuthHeader({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <View style={styles.container}>
      <View style={styles.mark}>
        <Text style={styles.markGlyph}>S</Text>
      </View>
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.subtitle}>{subtitle}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 4,
  },
  mark: {
    width: 48,
    height: 48,
    borderRadius: 14,
    backgroundColor: authColors.primary,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 20,
  },
  markGlyph: {
    color: authColors.primaryText,
    fontSize: 24,
    fontWeight: "800",
  },
  title: {
    fontSize: 28,
    fontWeight: "800",
    color: authColors.ink,
    letterSpacing: -0.4,
  },
  subtitle: {
    fontSize: 15,
    color: authColors.inkMuted,
    marginBottom: 12,
  },
});
