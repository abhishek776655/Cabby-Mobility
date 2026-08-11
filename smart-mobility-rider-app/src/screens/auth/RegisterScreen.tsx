import React, { useState } from "react";
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { AuthStackParamList } from "@/navigation/types";
import { register } from "@/api/auth";
import { useAuthStore } from "@/store/authStore";
import { toApiError, type ApiError } from "@/api/client";
import { authColors, authSpacing } from "@/constants/authTheme";
import { AuthHeader } from "@/components/ui/AuthHeader";
import { AuthTextField } from "@/components/ui/AuthTextField";
import { AuthPrimaryButton } from "@/components/ui/AuthPrimaryButton";
import { AuthErrorBanner } from "@/components/ui/AuthErrorBanner";

type Props = NativeStackScreenProps<AuthStackParamList, "Register">;

const MIN_PASSWORD_LENGTH = 8;

export function RegisterScreen({ navigation }: Props) {
  const setSession = useAuthStore((s) => s.setSession);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const passwordsMatch = password.length > 0 && password === confirmPassword;
  const passwordLongEnough = password.length >= MIN_PASSWORD_LENGTH;
  const canSubmit =
    email.trim().length > 0 && passwordLongEnough && passwordsMatch && !loading;

  const onSubmit = async () => {
    setLoading(true);
    setError(null);
    try {
      const auth = await register({ email: email.trim(), password, roles: ["RIDER"] });
      setSession(auth);
    } catch (e) {
      setError(toApiError(e as any));
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.safeArea} edges={["top", "bottom"]}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <ScrollView
          contentContainerStyle={styles.scrollContent}
          keyboardShouldPersistTaps="handled"
        >
          <AuthHeader title="Create your account" subtitle="Book rides in a couple of taps" />

          <View style={styles.form}>
            {error && <AuthErrorBanner message={error.message} />}

            <AuthTextField
              label="Email"
              placeholder="you@example.com"
              autoCapitalize="none"
              autoComplete="email"
              keyboardType="email-address"
              value={email}
              onChangeText={setEmail}
            />

            <AuthTextField
              label="Password"
              placeholder="At least 8 characters"
              secureTextEntry={!showPassword}
              autoCapitalize="none"
              value={password}
              onChangeText={setPassword}
              error={password.length > 0 && !passwordLongEnough}
              rightAccessory={
                <Pressable onPress={() => setShowPassword((v) => !v)} hitSlop={8}>
                  <Text style={styles.toggle}>{showPassword ? "Hide" : "Show"}</Text>
                </Pressable>
              }
            />

            <AuthTextField
              label="Confirm password"
              placeholder="Re-enter your password"
              secureTextEntry={!showPassword}
              autoCapitalize="none"
              value={confirmPassword}
              onChangeText={setConfirmPassword}
              error={confirmPassword.length > 0 && !passwordsMatch}
            />
            {confirmPassword.length > 0 && !passwordsMatch && (
              <Text style={styles.hint}>Passwords don't match</Text>
            )}

            <View style={styles.submitGroup}>
              <AuthPrimaryButton label="Sign up" onPress={onSubmit} loading={loading} disabled={!canSubmit} />
            </View>
          </View>

          <View style={styles.footer}>
            <Text style={styles.footerText}>Already have an account?</Text>
            <Pressable onPress={() => navigation.navigate("Login")} hitSlop={8}>
              <Text style={styles.footerLink}>Log in</Text>
            </Pressable>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: authColors.bg,
  },
  flex: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    justifyContent: "center",
    paddingHorizontal: authSpacing.lg,
    paddingVertical: authSpacing.xl,
  },
  form: {
    gap: authSpacing.md,
    marginTop: authSpacing.lg,
  },
  submitGroup: {
    marginTop: authSpacing.xs,
  },
  toggle: {
    color: authColors.primary,
    fontSize: 13,
    fontWeight: "700",
  },
  hint: {
    color: authColors.danger,
    fontSize: 13,
    marginTop: -8,
  },
  footer: {
    flexDirection: "row",
    justifyContent: "center",
    gap: 6,
    marginTop: authSpacing.xl,
  },
  footerText: {
    color: authColors.inkMuted,
    fontSize: 14,
  },
  footerLink: {
    color: authColors.primary,
    fontSize: 14,
    fontWeight: "700",
  },
});
