import React, { useState } from "react";
import { YStack, Text, Input, Button, Spinner } from "tamagui";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { AuthStackParamList } from "@/navigation/types";
import { register } from "@/api/auth";
import { useAuthStore } from "@/store/authStore";
import { toApiError, type ApiError } from "@/api/client";

type Props = NativeStackScreenProps<AuthStackParamList, "Register">;

export function RegisterScreen({ navigation }: Props) {
  const setSession = useAuthStore((s) => s.setSession);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const onSubmit = async () => {
    setLoading(true);
    setError(null);
    try {
      const auth = await register({ email, password, roles: ["RIDER"] });
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
        Create your account
      </Text>
      <Input placeholder="Email" autoCapitalize="none" keyboardType="email-address" value={email} onChangeText={setEmail} />
      <Input placeholder="Password" secureTextEntry value={password} onChangeText={setPassword} />
      {error && <Text color="$red10">{error.message}</Text>}
      <Button theme="active" onPress={onSubmit} disabled={loading}>
        {loading ? <Spinner /> : "Sign up"}
      </Button>
      <Button chromeless onPress={() => navigation.navigate("Login")}>
        I already have an account
      </Button>
    </YStack>
  );
}
