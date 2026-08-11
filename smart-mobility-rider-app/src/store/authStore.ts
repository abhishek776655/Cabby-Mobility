import { create } from "zustand";
import { persist } from "zustand/middleware";
import { zustandSecureStorage } from "@/lib/secureStorage";
import type { AuthResponse, Role } from "@/api/types";

export type AuthStatus = "idle" | "authenticated" | "guest";

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  userId: number | null;
  role: Role | null;
  status: AuthStatus;
  setSession: (auth: AuthResponse) => void;
  setTokens: (accessToken: string, refreshToken: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      userId: null,
      role: null,
      status: "idle",
      setSession: (auth) =>
        set({
          accessToken: auth.accessToken,
          refreshToken: auth.refreshToken,
          userId: auth.userId,
          role: auth.role,
          status: "authenticated",
        }),
      setTokens: (accessToken, refreshToken) => set({ accessToken, refreshToken }),
      logout: () =>
        set({
          accessToken: null,
          refreshToken: null,
          userId: null,
          role: null,
          status: "guest",
        }),
    }),
    {
      name: "auth-store",
      storage: {
        getItem: async (key) => {
          const value = await zustandSecureStorage.getItem(key);
          return value ? JSON.parse(value) : null;
        },
        setItem: async (key, value) => {
          await zustandSecureStorage.setItem(key, JSON.stringify(value));
        },
        removeItem: async (key) => {
          await zustandSecureStorage.removeItem(key);
        },
      },
    }
  )
);

/** Non-hook access for use outside React (axios interceptors, stomp client). */
export function getAuthState() {
  return useAuthStore.getState();
}
