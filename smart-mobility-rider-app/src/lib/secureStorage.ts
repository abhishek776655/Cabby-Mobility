import * as SecureStore from "expo-secure-store";

/**
 * Wraps expo-secure-store (iOS Keychain / Android Keystore) for auth tokens.
 * Never use AsyncStorage for tokens — it's unencrypted on-device storage.
 */
export const secureStorage = {
  async getItem(key: string): Promise<string | null> {
    return SecureStore.getItemAsync(key);
  },
  async setItem(key: string, value: string): Promise<void> {
    await SecureStore.setItemAsync(key, value);
  },
  async removeItem(key: string): Promise<void> {
    await SecureStore.deleteItemAsync(key);
  },
};

/**
 * Adapter matching zustand's persist StateStorage interface, backed by secureStorage.
 * Used for authStore, which must not persist via plain AsyncStorage.
 */
export const zustandSecureStorage = {
  getItem: (key: string) => secureStorage.getItem(key),
  setItem: (key: string, value: string) => secureStorage.setItem(key, value),
  removeItem: (key: string) => secureStorage.removeItem(key),
};
