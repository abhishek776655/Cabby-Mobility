import Constants from "expo-constants";

interface AppExtra {
  apiBaseUrl: string;
  wsUrl: string;
}

function getExtra(): AppExtra {
  const extra = Constants.expoConfig?.extra as Partial<AppExtra> | undefined;
  return {
    apiBaseUrl: extra?.apiBaseUrl ?? "http://localhost:8080",
    wsUrl: extra?.wsUrl ?? "ws://localhost:8080/ws",
  };
}

export const env = getExtra();
