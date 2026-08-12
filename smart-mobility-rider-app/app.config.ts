import type { ExpoConfig } from "expo/config";
import { networkInterfaces } from "node:os";

/**
 * A phone running the dev client can't reach the backend on "localhost" — that resolves to the
 * phone itself, not this machine. It needs this machine's LAN address, which DHCP reassigns
 * every so often (it has already moved once during development). Detecting it at config time
 * keeps `npm run start` a single command that keeps working, instead of a .env holding an IP
 * that silently goes stale and surfaces later as unexplained network/socket failures.
 */
function detectLanHost(): string {
  for (const addresses of Object.values(networkInterfaces())) {
    for (const address of addresses ?? []) {
      if (address.family === "IPv4" && !address.internal) return address.address;
    }
  }
  // Simulators/emulators share the host's loopback, so this is still correct there.
  return "localhost";
}

const host = process.env.LAN_HOST ?? detectLanHost();
const apiPort = process.env.API_PORT ?? "8080";
const wsPort = process.env.WS_PORT ?? "8080";

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
    apiBaseUrl: process.env.API_BASE_URL ?? `http://${host}:${apiPort}`,
    wsUrl: process.env.WS_URL ?? `ws://${host}:${wsPort}/ws`,
  },
};

export default config;
