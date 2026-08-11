import { useAuthStore } from "@/store/authStore";
import type { AuthResponse } from "@/api/types";

describe("authStore", () => {
  beforeEach(() => {
    useAuthStore.setState({
      accessToken: null,
      refreshToken: null,
      userId: null,
      role: null,
      status: "idle",
    });
  });

  it("setSession stores tokens and marks authenticated", () => {
    const auth: AuthResponse = {
      accessToken: "at",
      refreshToken: "rt",
      userId: 42,
      role: "RIDER",
    };

    useAuthStore.getState().setSession(auth);

    const state = useAuthStore.getState();
    expect(state.accessToken).toBe("at");
    expect(state.refreshToken).toBe("rt");
    expect(state.userId).toBe(42);
    expect(state.status).toBe("authenticated");
  });

  it("setTokens updates tokens without touching userId/role", () => {
    useAuthStore.setState({ userId: 1, role: "RIDER", status: "authenticated" });

    useAuthStore.getState().setTokens("new-at", "new-rt");

    const state = useAuthStore.getState();
    expect(state.accessToken).toBe("new-at");
    expect(state.refreshToken).toBe("new-rt");
    expect(state.userId).toBe(1);
  });

  it("logout clears the session and marks guest", () => {
    useAuthStore.setState({
      accessToken: "at",
      refreshToken: "rt",
      userId: 1,
      role: "RIDER",
      status: "authenticated",
    });

    useAuthStore.getState().logout();

    const state = useAuthStore.getState();
    expect(state.accessToken).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.status).toBe("guest");
  });
});
