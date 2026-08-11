import React, { useState } from "react";
import { YStack, Text, Input, Button, Spinner } from "tamagui";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { AuthStackParamList } from "@/navigation/types";
import { login } from "@/api/auth";
import { useAuthStore } from "@/store/authStore";
import type { ApiError } from "@/api/client";
import { toApiError } from "@/api/client";

type Props = NativeStackScreenProps<AuthStackParamList, "Login">;

export function LoginScreen({ navigation }: Props) {
  const setSession = useAuthStore((s) => s.setSession);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const onSubmit = async () => {
    setLoading(true);
    setError(null);
    try {
      const auth = await login({ email, password });
      setSession(auth);
    } catch (e) {
      setError(toApiError(e as any));
    } finally {
      setLoading(false);
    }
  };

  return (
    <YStack flex={1} justifyContent="center" padding="$4" gap="$3">
      <Text fontSize="$8" fontWeight="700">
        Smart Mobility
      </Text>
      <Input placeholder="Email" autoCapitalize="none" keyboardType="email-address" value={email} onChangeText={setEmail} />
      <Input placeholder="Password" secureTextEntry value={password} onChangeText={setPassword} />
      {error && <Text color="$red10">{error.message}</Text>}
      <Button theme="active" onPress={onSubmit} disabled={loading}>
        {loading ? <Spinner /> : "Log in"}
      </Button>
      <Button chromeless onPress={() => navigation.navigate("Register")}>
        Create an account
      </Button>
    </YStack>
  );
}
