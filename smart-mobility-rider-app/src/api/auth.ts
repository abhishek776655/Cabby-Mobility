import { apiClient, unwrap } from "./client";
import type { ApiEnvelope, AuthResponse, Role } from "./types";

export interface RegisterRequest {
  email: string;
  password: string;
  roles: Role[];
}

export interface LoginRequest {
  email: string;
  password: string;
}

export async function register(body: RegisterRequest): Promise<AuthResponse> {
  const { data } = await apiClient.post<ApiEnvelope<AuthResponse> | AuthResponse>(
    "/auth/register",
    body,
    { skipAuth: true }
  );
  return unwrap(data);
}

export async function login(body: LoginRequest): Promise<AuthResponse> {
  const { data } = await apiClient.post<ApiEnvelope<AuthResponse> | AuthResponse>(
    "/auth/login",
    body,
    { skipAuth: true }
  );
  return unwrap(data);
}

export async function logout(): Promise<void> {
  await apiClient.post("/auth/logout");
}
