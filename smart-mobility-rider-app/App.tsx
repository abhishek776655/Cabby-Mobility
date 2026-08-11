import "text-encoding"; // TextEncoder/TextDecoder polyfill @stomp/stompjs needs on RN's JS engine
import "react-native-gesture-handler";
import React from "react";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { TamaguiProvider } from "tamagui";
import { StatusBar } from "expo-status-bar";
import tamaguiConfig from "./tamagui.config";
import { RootNavigator } from "@/navigation/RootNavigator";

export default function App() {
  return (
    <TamaguiProvider config={tamaguiConfig}>
      <SafeAreaProvider>
        <StatusBar style="auto" />
        <RootNavigator />
      </SafeAreaProvider>
    </TamaguiProvider>
  );
}
