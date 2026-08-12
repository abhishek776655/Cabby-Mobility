import React from "react";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import type { AppTabsParamList } from "./types";
import { HomeMapScreen } from "@/screens/home/HomeMapScreen";
import { WalletScreen } from "@/screens/wallet/WalletScreen";
import { RideHistoryScreen } from "@/screens/history/RideHistoryScreen";
import { ProfileScreen } from "@/screens/profile/ProfileScreen";
import { CustomTabBar } from "./CustomTabBar";

const Tab = createBottomTabNavigator<AppTabsParamList>();

export function AppTabs() {
  return (
    <Tab.Navigator tabBar={(props) => <CustomTabBar {...props} />}>
      {/* Home is a full-bleed map with its own bottom panel; a title bar would just crop the
          map and duplicate the tab label. */}
      <Tab.Screen name="Home" component={HomeMapScreen} options={{ headerShown: false }} />
      <Tab.Screen name="Wallet" component={WalletScreen} />
      <Tab.Screen name="History" component={RideHistoryScreen} options={{ title: "History" }} />
      <Tab.Screen name="Profile" component={ProfileScreen} />
    </Tab.Navigator>
  );
}
