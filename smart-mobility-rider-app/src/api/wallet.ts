import { apiClient, unwrap } from "./client";
import type { ApiEnvelope, WalletBalance, WalletTransaction } from "./types";

export async function getBalance(): Promise<WalletBalance> {
  const { data } = await apiClient.get<ApiEnvelope<WalletBalance> | WalletBalance>(
    "/wallet/me"
  );
  return unwrap(data);
}

export async function topup(amount: number, referenceId: string): Promise<void> {
  await apiClient.post("/wallet/me/topup", { amount, referenceId });
}

export async function getTransactions(): Promise<WalletTransaction[]> {
  const { data } = await apiClient.get<
    ApiEnvelope<WalletTransaction[]> | WalletTransaction[]
  >("/wallet/me/transactions");
  return unwrap(data);
}
