import React, { useEffect, useRef, useState } from "react";
import { Animated, LayoutChangeEvent, Pressable, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import type { BottomTabBarProps } from "@react-navigation/bottom-tabs";
import { colors, spacing } from "@/constants/theme";

type IoniconName = React.ComponentProps<typeof Ionicons>["name"];

// Outline/filled pairs rather than one icon dimmed by colour alone — the active tab needs a
// shape change too, not just a tint, so it still reads correctly for a colour-blind rider.
const TAB_ICONS: Record<string, { active: IoniconName; inactive: IoniconName }> = {
  Home: { active: "home", inactive: "home-outline" },
  Wallet: { active: "wallet", inactive: "wallet-outline" },
  History: { active: "time", inactive: "time-outline" },
  Profile: { active: "person", inactive: "person-outline" },
};

/**
 * Floating pill tab bar: rounded, inset from the screen edges, with an animated indicator
 * that slides under whichever tab is active rather than just recolouring an icon. Home's map
 * and its own bottom panel are unaffected — react-navigation still reserves this bar's height
 * above the screen content, this only changes how the bar itself is drawn.
 */
export function CustomTabBar({ state, descriptors, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();
  const [tabWidth, setTabWidth] = useState(0);
  const indicatorX = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (!tabWidth) return;
    Animated.spring(indicatorX, {
      toValue: state.index * tabWidth,
      useNativeDriver: true,
      bounciness: 6,
      speed: 14,
    }).start();
  }, [state.index, tabWidth, indicatorX]);

  const onBarLayout = (e: LayoutChangeEvent) => {
    setTabWidth(e.nativeEvent.layout.width / state.routes.length);
  };

  return (
    // Deliberately NOT position:"absolute" — react-navigation gives the tab bar a normal flex
    // slot below the screen content and sizes that content to what's left. An absolute wrap
    // would zero out this component's contribution to that layout, so every other tab's list
    // content would render right up to (and under) the floating bar. The floating look comes
    // from padding insetting the pill from the real screen edges, not from stacking over them.
    <View style={[styles.wrap, { paddingBottom: Math.max(insets.bottom, spacing.sm) }]}>
      <View style={styles.bar} onLayout={onBarLayout}>
        {tabWidth > 0 && (
          <Animated.View
            style={[
              styles.indicator,
              {
                width: tabWidth,
                transform: [{ translateX: indicatorX }],
              },
            ]}
          >
            <View style={styles.indicatorPill} />
          </Animated.View>
        )}

        {state.routes.map((route, index) => {
          const { options } = descriptors[route.key];
          const focused = state.index === index;
          const icons = TAB_ICONS[route.name] ?? TAB_ICONS.Home;
          const label =
            options.tabBarLabel !== undefined
              ? String(options.tabBarLabel)
              : options.title !== undefined
                ? options.title
                : route.name;

          const onPress = () => {
            const event = navigation.emit({ type: "tabPress", target: route.key, canPreventDefault: true });
            if (!focused && !event.defaultPrevented) {
              navigation.navigate(route.name);
            }
          };

          return (
            <Pressable
              key={route.key}
              onPress={onPress}
              style={styles.tab}
              accessibilityRole="button"
              accessibilityState={focused ? { selected: true } : {}}
              accessibilityLabel={options.tabBarAccessibilityLabel ?? label}
            >
              <Ionicons
                name={focused ? icons.active : icons.inactive}
                size={22}
                color={focused ? colors.primary : colors.inkMuted}
              />
              <Text style={[styles.label, focused && styles.labelActive]} numberOfLines={1}>
                {label}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

const BAR_HEIGHT = 64;

const styles = StyleSheet.create({
  wrap: {
    backgroundColor: colors.bg,
    paddingHorizontal: spacing.md,
    paddingTop: spacing.xs,
  },
  bar: {
    flexDirection: "row",
    height: BAR_HEIGHT,
    // Past radii.lg (20) on purpose — this floats free of the screen edges, so it reads as an
    // object rather than a dock, and wants a rounder corner than the sheets/cards it sits
    // above to look distinct from them too.
    borderRadius: 32,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    shadowColor: colors.ink,
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.12,
    shadowRadius: 18,
    elevation: 10,
    overflow: "hidden",
  },
  indicator: {
    position: "absolute",
    // Tighter than before (was 8) — with the pill now sharing the bar's own radius, a wide
    // gap read as a smaller chip floating inside the bar rather than a close fit against it.
    top: 4,
    bottom: 4,
    alignItems: "center",
  },
  indicatorPill: {
    width: "72%",
    height: "100%",
    // Matches the outer bar's radius exactly, not a smaller inset one — the active pill reads
    // as a piece cut from the same shape as its container, not an unrelated chip inside it.
    borderRadius: 32,
    backgroundColor: colors.primarySoft,
  },
  tab: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 3,
  },
  label: {
    fontSize: 11,
    fontWeight: "600",
    color: colors.inkMuted,
  },
  labelActive: {
    color: colors.primary,
    fontWeight: "700",
  },
});
