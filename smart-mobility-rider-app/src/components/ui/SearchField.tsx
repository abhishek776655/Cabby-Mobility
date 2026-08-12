import React, { forwardRef } from "react";
import { ActivityIndicator, Pressable, StyleSheet, TextInput, View, type TextInputProps } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors, radii } from "@/constants/theme";

interface Props extends Omit<TextInputProps, "style"> {
  value: string;
  onChangeText: (text: string) => void;
  placeholder: string;
  focused?: boolean;
  loading?: boolean;
  onClear?: () => void;
}

/**
 * Design-system text input. Owns its own focus ring and clear affordance so callers never
 * hand-roll input styling; composition (rails, labels, grouping) belongs to the caller.
 *
 * Placeholder uses inkMuted rather than inkFaint: inkFaint is ~2.4:1 on white, well under the
 * 4.5:1 needed for text a rider has to read before they know what to type.
 */
export const SearchField = forwardRef<TextInput, Props>(function SearchField(
  { value, onChangeText, placeholder, focused, loading, onClear, ...rest },
  ref
) {
  const showClear = value.length > 0 && !loading;

  return (
    <View style={[styles.container, focused && styles.containerFocused]}>
      <TextInput
        ref={ref}
        style={styles.input}
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={colors.inkMuted}
        autoCorrect={false}
        autoCapitalize="words"
        returnKeyType="search"
        {...rest}
      />
      {loading && <ActivityIndicator size="small" color={colors.inkMuted} style={styles.trailing} />}
      {showClear && (
        <Pressable
          onPress={onClear}
          style={styles.trailing}
          // The glyph is 16px; without this the touch target is far under the 44pt minimum.
          hitSlop={12}
          accessibilityRole="button"
          accessibilityLabel={`Clear ${placeholder.toLowerCase()}`}
        >
          <View style={styles.clearCircle}>
            <Ionicons name="close" size={12} color={colors.surface} />
          </View>
        </Pressable>
      )}
    </View>
  );
});

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    alignItems: "center",
    height: 48,
    borderRadius: radii.md,
    backgroundColor: colors.surfaceSunken,
    paddingHorizontal: 14,
    borderWidth: 1.5,
    borderColor: "transparent",
  },
  containerFocused: {
    borderColor: colors.borderFocus,
    backgroundColor: colors.surface,
  },
  input: {
    flex: 1,
    fontSize: 16,
    fontWeight: "600",
    color: colors.ink,
    // Android centres text oddly in a fixed-height row without this.
    paddingVertical: 0,
  },
  trailing: {
    marginLeft: 10,
  },
  clearCircle: {
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: colors.inkFaint,
    alignItems: "center",
    justifyContent: "center",
  },
});
