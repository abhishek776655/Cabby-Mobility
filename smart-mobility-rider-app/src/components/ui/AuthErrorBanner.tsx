import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { authColors } from "@/constants/authTheme";

export function AuthErrorBanner({ message }: { message: string }) {
  return (
    <View style={styles.container}>
      <Text style={styles.text}>{message}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: authColors.dangerBg,
    borderColor: authColors.dangerBorder,
    borderWidth: 1,
    borderRadius: 12,
    paddingVertical: 12,
    paddingHorizontal: 14,
  },
  text: {
    color: authColors.danger,
    fontSize: 14,
    fontWeight: "600",
  },
});
