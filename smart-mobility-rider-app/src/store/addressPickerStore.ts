import { create } from "zustand";

export type AddressField = "pickup" | "drop";

export interface PickedAddress {
  label: string;
  address: string;
  latitude: number;
  longitude: number;
}

interface AddressPickerState {
  /** Which field the open search screen is editing. */
  field: AddressField | null;
  /** Set by the search screen on selection; consumed and cleared by the screen that opened it. */
  result: PickedAddress | null;
  openFor: (field: AddressField) => void;
  submit: (address: PickedAddress) => void;
  consume: () => void;
}

/**
 * Hand-off channel between a booking screen and the full-screen address search.
 *
 * React Navigation params are the obvious alternative, but the natural shape here is a
 * callback ("give me back the place the rider picked"), and non-serializable params break
 * state persistence and deep links. A tiny store keeps the search screen reusable by any
 * caller without each one needing its own return-param plumbing.
 *
 * Not persisted: an in-flight address pick is meaningless after a relaunch.
 */
export const useAddressPickerStore = create<AddressPickerState>((set) => ({
  field: null,
  result: null,
  openFor: (field) => set({ field, result: null }),
  submit: (address) => set({ result: address }),
  consume: () => set({ field: null, result: null }),
}));
