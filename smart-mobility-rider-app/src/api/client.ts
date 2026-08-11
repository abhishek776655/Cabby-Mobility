import axios, { AxiosError, AxiosRequestConfig, AxiosInstance } from "axios";
import { API_BASE_URL } from "@/constants/config";
import { getAuthState } from "@/store/authStore";
import type { ApiEnvelope, AuthResponse } from "./types";

export interface ApiError {
  status: number | null;
  message: string;
  isNetworkError: boolean;
  isRateLimited: boolean;
  isCircuitOpen: boolean;
}

declare module "axios" {
  export interface AxiosRequestConfig {
    skipAuth?: boolean;
    /** Internal flag to prevent infinite retry loops on the refresh flow itself. */
    _retried?: boolean;
  }
}

export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
});

apiClient.interceptors.request.use((config) => {
  if (config.skipAuth) {
    return config;
  }
  const { accessToken } = getAuthState();
  if (accessToken) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

// Single-flight refresh: concurrent 401s share one in-flight refresh call instead of each
// independently hitting /auth/refresh (a "refresh storm").
let refreshPromise: Promise<AuthResponse> | null = null;

async function refreshTokens(): Promise<AuthResponse> {
  const { refreshToken } = getAuthState();
  if (!refreshToken) {
    throw new Error("No refresh token available");
  }
  const response = await axios.post<ApiEnvelope<AuthResponse> | AuthResponse>(
    `${API_BASE_URL}/auth/refresh`,
    { refreshToken }
  );
  const body = response.data as any;
  return body.data ?? body;
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as AxiosRequestConfig | undefined;

    if (
      error.response?.status === 401 &&
      config &&
      !config.skipAuth &&
      !config._retried
    ) {
      config._retried = true;
      try {
        if (!refreshPromise) {
          refreshPromise = refreshTokens();
        }
        const auth = await refreshPromise;
        getAuthState().setTokens(auth.accessToken, auth.refreshToken);
        config.headers = config.headers ?? {};
        (config.headers as any).Authorization = `Bearer ${auth.accessToken}`;
        return apiClient.request(config);
      } catch (refreshError) {
        getAuthState().logout();
        return Promise.reject(toApiError(error));
      } finally {
        refreshPromise = null;
      }
    }

    return Promise.reject(toApiError(error));
  }
);

export function toApiError(error: AxiosError): ApiError {
  const status = error.response?.status ?? null;
  const backendMessage =
    (error.response?.data as any)?.message ??
    (error.response?.data as any)?.error ??
    error.message;

  return {
    status,
    message: backendMessage ?? "Something went wrong",
    isNetworkError: !error.response,
    isRateLimited: status === 429,
    isCircuitOpen: status === 503,
  };
}

/** Unwraps the backend's {success, data, message} envelope, used by every service. */
export function unwrap<T>(envelope: ApiEnvelope<T> | T): T {
  if (envelope && typeof envelope === "object" && "data" in (envelope as any)) {
    return (envelope as ApiEnvelope<T>).data;
  }
  return envelope as T;
}
