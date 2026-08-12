import { create } from "zustand";
import { persist, type PersistOptions } from "zustand/middleware";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { createJSONStorage } from "zustand/middleware";
import type { PickedAddress } from "./addressPickerStore";

const MAX_RECENTS = 6;

function normalize(value: string): string {
  return value.trim().toLowerCase().replace(/\s+/g, " ");
}

/** Rows that describe wherever the rider happens to be, rather than a fixed place. */
function isLivePosition(address: PickedAddress): boolean {
  const label = normalize(address.label);
  return label === "current location" || label === "your current location";
}

/** Equirectangular approximation — plenty at the scale of a single city. */
function distanceMeters(a: PickedAddress, b: PickedAddress): number {
  const metresPerDegreeLat = 111_320;
  const latRadians = (a.latitude * Math.PI) / 180;
  const dy = (a.latitude - b.latitude) * metresPerDegreeLat;
  const dx = (a.longitude - b.longitude) * metresPerDegreeLat * Math.cos(latRadians);
  return Math.hypot(dx, dy);
}

interface RecentSearchesState {
  recents: PickedAddress[];
  remember: (address: PickedAddress) => void;
  clear: () => void;
}

type Persisted = Pick<RecentSearchesState, "recents">;

/**
 * Places the rider has actually picked before, most recent first.
 *
 * Persisted (unlike the picker hand-off store) because its whole value is surviving restarts —
 * a "recent" list that empties every launch is just a blank panel. AsyncStorage rather than
 * SecureStore: these are convenience entries, not credentials.
 */
export const useRecentSearchesStore = create<RecentSearchesState>()(
  persist<RecentSearchesState, [], [], Persisted>(
    (set) => ({
      recents: [],
      remember: (address) =>
        set((state) => {
          // The rider's live position is not a place — every fix lands on slightly different
          // coordinates, so remembering it fills the list with near-identical rows that all
          // read "Current location".
          if (isLivePosition(address)) return state;

          const isSame = (a: PickedAddress) =>
            // Same text is the same place regardless of which fix produced the coordinates...
            normalize(a.address) === normalize(address.address) ||
            // ...and the same spot reached by search vs a dropped pin carries different text
            // but must still collapse. ~100m, wide enough to absorb pin-drop jitter without
            // merging genuinely distinct addresses on a street.
            distanceMeters(a, address) < 100;

          return {
            recents: [address, ...state.recents.filter((a) => !isSame(a))].slice(0, MAX_RECENTS),
          };
        }),
      clear: () => set({ recents: [] }),
    }),
    {
      name: "recent-searches",
      storage: createJSONStorage(() => AsyncStorage),
      partialize: (state) => ({ recents: state.recents }),
    } as PersistOptions<RecentSearchesState, Persisted>
  )
);
