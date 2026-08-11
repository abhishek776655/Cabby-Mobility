import React, { useCallback, useState } from "react";
import { YStack, XStack, Text, Button, Spinner, Input, ListItem } from "tamagui";
import { useFocusEffect } from "@react-navigation/native";
import { getBalance, getTransactions, topup } from "@/api/wallet";
import { useWalletStore } from "@/store/walletStore";
import { toApiError, type ApiError } from "@/api/client";

export function WalletScreen() {
  const balance = useWalletStore((s) => s.balance);
  const transactions = useWalletStore((s) => s.transactions);
  const setBalance = useWalletStore((s) => s.setBalance);
  const setTransactions = useWalletStore((s) => s.setTransactions);

  const [loading, setLoading] = useState(true);
  const [topupAmount, setTopupAmount] = useState("");
  const [error, setError] = useState<ApiError | null>(null);

  const refresh = useCallback(() => {
    setLoading(true);
    Promise.all([getBalance(), getTransactions()])
      .then(([balanceRes, txns]) => {
        setBalance(balanceRes.balance);
        setTransactions(txns);
        setError(null);
      })
      .catch((e) => setError(toApiError(e)))
      .finally(() => setLoading(false));
  }, [setBalance, setTransactions]);

  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh])
  );

  const onTopup = async () => {
    const amount = Math.round(parseFloat(topupAmount) * 100);
    if (!amount || amount <= 0) return;
    await topup(amount, `manual-${Date.now()}`);
    setTopupAmount("");
    refresh();
  };

  if (loading && balance === null) {
    return (
      <YStack flex={1} justifyContent="center" alignItems="center">
        <Spinner size="large" />
      </YStack>
    );
  }

  return (
    <YStack flex={1} padding="$4" gap="$4">
      <YStack alignItems="center" gap="$1">
        <Text color="$gray10">Balance</Text>
        <Text fontSize="$10" fontWeight="800" color={(balance ?? 0) < 0 ? "$red10" : undefined}>
          ₹{balance != null ? (balance / 100).toFixed(2) : "—"}
        </Text>
      </YStack>
      {error && <Text color="$red10">{error.message}</Text>}
      <XStack gap="$2">
        <Input
          flex={1}
          placeholder="Amount (₹)"
          keyboardType="numeric"
          value={topupAmount}
          onChangeText={setTopupAmount}
        />
        <Button theme="active" onPress={onTopup}>
          Top up
        </Button>
      </XStack>
      <Text fontWeight="600">Transactions</Text>
      {transactions.map((txn) => (
        <ListItem
          key={txn.id}
          title={`${txn.type} ₹${(txn.amount / 100).toFixed(2)}`}
          subTitle={new Date(txn.createdAt).toLocaleString()}
        />
      ))}
      {transactions.length === 0 && <Text color="$gray10">No transactions yet.</Text>}
    </YStack>
  );
}
