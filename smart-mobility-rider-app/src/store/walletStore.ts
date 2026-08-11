import { create } from "zustand";
import type { WalletTransaction } from "@/api/types";

interface WalletState {
  balance: number | null;
  transactions: WalletTransaction[];
  setBalance: (balance: number) => void;
  setTransactions: (transactions: WalletTransaction[]) => void;
}

// Not persisted — always refetch on Wallet screen focus. The in-memory cache here just
// avoids a loading flash on repeat visits within the same session.
export const useWalletStore = create<WalletState>((set) => ({
  balance: null,
  transactions: [],
  setBalance: (balance) => set({ balance }),
  setTransactions: (transactions) => set({ transactions }),
}));
