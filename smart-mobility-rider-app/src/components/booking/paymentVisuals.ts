import type { ComponentProps } from "react";
import { Ionicons } from "@expo/vector-icons";
import type { PreferredPaymentMethod } from "@/api/types";

type IoniconName = ComponentProps<typeof Ionicons>["name"];

interface PaymentVisual {
  value: PreferredPaymentMethod;
  label: string;
  icon: IoniconName;
}

export const PAYMENT_METHODS: PaymentVisual[] = [
  { value: "CASH", label: "Cash", icon: "cash-outline" },
  { value: "CARD", label: "Card", icon: "card-outline" },
  { value: "WALLET", label: "Wallet", icon: "wallet-outline" },
];

export function getPaymentVisual(method: PreferredPaymentMethod): PaymentVisual {
  return PAYMENT_METHODS.find((m) => m.value === method) ?? PAYMENT_METHODS[0];
}
