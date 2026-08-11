import axios from "axios";
import { apiClient, toApiError } from "@/api/client";
import { useAuthStore } from "@/store/authStore";

jest.mock("axios", () => {
  const actual = jest.requireActual("axios");
  return {
    ...actual,
    post: jest.fn(),
  };
});

describe("apiClient 401 refresh flow", () => {
  beforeEach(() => {
    useAuthStore.setState({
      accessToken: "old-token",
      refreshToken: "refresh-token",
      userId: 1,
      role: "RIDER",
      status: "authenticated",
    });
    jest.clearAllMocks();
  });

  it("attaches the current access token to outgoing requests", async () => {
    const config: any = { headers: {} };
    const handlers = (apiClient.interceptors.request as any).handlers;
    const result = handlers[0].fulfilled(config);
    expect(result.headers.Authorization).toBe("Bearer old-token");
  });

  it("skips the auth header when skipAuth is set", async () => {
    const config: any = { headers: {}, skipAuth: true };
    const handlers = (apiClient.interceptors.request as any).handlers;
    const result = handlers[0].fulfilled(config);
    expect(result.headers.Authorization).toBeUndefined();
  });

  it("refreshes the token once and retries on 401, sharing one refresh across concurrent callers", async () => {
    (axios.post as jest.Mock).mockResolvedValue({
      data: { accessToken: "new-token", refreshToken: "new-refresh", userId: 1, role: "RIDER" },
    });

    const retriedRequest = jest.spyOn(apiClient, "request").mockResolvedValue({ data: "ok" } as any);

    const handlers = (apiClient.interceptors.response as any).handlers;
    const rejected = handlers[0].rejected;

    const error1 = {
      response: { status: 401 },
      config: { headers: {}, skipAuth: false, _retried: false },
    };
    const error2 = {
      response: { status: 401 },
      config: { headers: {}, skipAuth: false, _retried: false },
    };

    await Promise.all([rejected(error1), rejected(error2)]);

    // Both 401s should share a single refresh call, not two.
    expect(axios.post).toHaveBeenCalledTimes(1);
    expect(useAuthStore.getState().accessToken).toBe("new-token");
    expect(retriedRequest).toHaveBeenCalledTimes(2);

    retriedRequest.mockRestore();
  });

  it("logs the user out when refresh itself fails", async () => {
    (axios.post as jest.Mock).mockRejectedValue(new Error("refresh token expired"));

    const handlers = (apiClient.interceptors.response as any).handlers;
    const rejected = handlers[0].rejected;

    const error = {
      response: { status: 401 },
      config: { headers: {}, skipAuth: false, _retried: false },
      message: "Unauthorized",
    };

    await expect(rejected(error)).rejects.toBeDefined();
    expect(useAuthStore.getState().status).toBe("guest");
    expect(useAuthStore.getState().accessToken).toBeNull();
  });

  it("does not attempt refresh for requests that already retried once", async () => {
    const handlers = (apiClient.interceptors.response as any).handlers;
    const rejected = handlers[0].rejected;

    const error = {
      response: { status: 401 },
      config: { headers: {}, skipAuth: false, _retried: true },
      message: "Unauthorized",
    };

    await expect(rejected(error)).rejects.toBeDefined();
    expect(axios.post).not.toHaveBeenCalled();
  });
});

describe("toApiError", () => {
  it("flags network errors when there is no response", () => {
    const error = { message: "Network Error" } as any;
    const result = toApiError(error);
    expect(result.isNetworkError).toBe(true);
    expect(result.status).toBeNull();
  });

  it("flags rate limiting on 429", () => {
    const error = { response: { status: 429, data: {} }, message: "Too many" } as any;
    const result = toApiError(error);
    expect(result.isRateLimited).toBe(true);
  });

  it("flags circuit-open on 503", () => {
    const error = { response: { status: 503, data: {} }, message: "unavailable" } as any;
    const result = toApiError(error);
    expect(result.isCircuitOpen).toBe(true);
  });

  it("prefers the backend message when present", () => {
    const error = {
      response: { status: 400, data: { message: "Invalid vehicle type" } },
      message: "Bad Request",
    } as any;
    const result = toApiError(error);
    expect(result.message).toBe("Invalid vehicle type");
  });
});
