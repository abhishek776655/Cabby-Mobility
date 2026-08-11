import { Client } from "@stomp/stompjs";
import { tripStompClient } from "@/realtime/stompClient";
import { useAuthStore } from "@/store/authStore";

jest.mock("@stomp/stompjs", () => {
  const instances: any[] = [];
  class MockClient {
    connectHeaders: Record<string, string> = {};
    beforeConnect: (() => void) | undefined;
    onConnect: (() => void) | undefined;
    active = false;
    connected = false;
    constructor(opts: any) {
      this.beforeConnect = opts.beforeConnect;
      this.onConnect = opts.onConnect;
      instances.push(this);
    }
    activate() {
      this.beforeConnect?.();
      this.active = true;
      this.connected = true;
    }
    deactivate() {
      this.active = false;
      this.connected = false;
    }
    subscribe() {
      return { unsubscribe: jest.fn() };
    }
  }
  (MockClient as any).__instances = instances;
  return { Client: MockClient };
});

describe("tripStompClient", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "token-v1" });
    tripStompClient.disconnect();
    (Client as any).__instances?.length && ((Client as any).__instances.length = 0);
  });

  it("populates connectHeaders with the current access token on connect", () => {
    tripStompClient.connect("ride-1", jest.fn());

    const instance = (Client as any).__instances[0];
    expect(instance.connectHeaders.Authorization).toBe("Bearer token-v1");
  });

  it("re-reads the token on reconnect so a refreshed token is not stale", () => {
    tripStompClient.connect("ride-1", jest.fn());
    tripStompClient.disconnect();

    useAuthStore.setState({ accessToken: "token-v2" });
    tripStompClient.connect("ride-1", jest.fn());

    const instances = (Client as any).__instances;
    const latest = instances[instances.length - 1];
    expect(latest.connectHeaders.Authorization).toBe("Bearer token-v2");
  });
});
