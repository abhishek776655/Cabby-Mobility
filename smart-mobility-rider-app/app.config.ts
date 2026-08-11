import type { ExpoConfig } from "expo/config";

const config: ExpoConfig = {
  name: "Smart Mobility Rider",
  slug: "smart-mobility-rider-app",
  version: "1.0.0",
  orientation: "portrait",
  userInterfaceStyle: "automatic",
  assetBundlePatterns: ["**/*"],
  plugins: [
    [
      "expo-location",
      {
        locationAlwaysAndWhenInUsePermission:
          "Allow Smart Mobility to use your location to find nearby drivers and show your pickup point.",
      },
    ],
    "expo-secure-store",
    "expo-status-bar",
  ],
  ios: {
    bundleIdentifier: "com.anonymous.smart-mobility-rider-app",
    supportsTablet: false,
    config: {
      googleMapsApiKey: process.env.GOOGLE_MAPS_IOS_API_KEY,
    },
  },
  android: {
    config: {
      googleMaps: {
        apiKey: process.env.GOOGLE_MAPS_ANDROID_API_KEY,
      },
    },
  },
  extra: {
    apiBaseUrl: process.env.API_BASE_URL ?? "http://localhost:8080",
    wsUrl: process.env.WS_URL ?? "ws://localhost:8080/ws",
  },
};

export default config;
