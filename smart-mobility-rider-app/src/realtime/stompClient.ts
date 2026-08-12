// @stomp/stompjs (with forceBinaryWSFrames) encodes every outgoing frame via
// `new TextEncoder().encode(...)`. This package was already listed as a dependency for
// exactly this reason but never actually imported anywhere, so RN's TextEncoder — missing or
// broken depending on the Hermes build — silently corrupted every outgoing STOMP frame,
// which the server then rejected as a protocol violation (WS close code 1002) regardless of
// a valid token or correctly-formed frame content.
import "text-encoding";
import { Client, IMessage } from "@stomp/stompjs";
import { WS_URL, STOMP_RECONNECT_DELAY_MS, STOMP_HEARTBEAT_MS } from "@/constants/config";
import { getAuthState } from "@/store/authStore";
import type { DriverLocationUpdatedEvent } from "@/api/types";

/**
 * Singleton STOMP connection manager. The gateway skips JWT validation entirely for
 * /ws/** — auth happens in-band on the STOMP CONNECT frame itself (realtime-gateway-service's
 * StompAuthChannelInterceptor), so the token must go in connectHeaders, not any HTTP header
 * on the initial WebSocket upgrade request.
 */
class TripStompClient {
  private client: Client | null = null;
  private currentRideId: string | null = null;
  private lastError: string | null = null;

  connect(rideId: string, onLocation: (event: DriverLocationUpdatedEvent) => void): void {
    if (this.client?.active && this.currentRideId === rideId) {
      return;
    }
    this.disconnect();
    this.currentRideId = rideId;
    this.lastError = null;

    console.log(`[stomp] connecting to ${WS_URL} for ride ${rideId}`);

    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: STOMP_RECONNECT_DELAY_MS,
      heartbeatIncoming: STOMP_HEARTBEAT_MS,
      heartbeatOutgoing: STOMP_HEARTBEAT_MS,
      // React Native's WebSocket chops the NULL byte that terminates a STOMP frame, so the
      // server upgraded the socket but never parsed a CONNECT frame from it (visible as a
      // transport disconnect with no matching SessionConnectedEvent, and no auth rejection).
      // Sending frames as binary sidesteps it — this is @stomp/stompjs's documented RN
      // workaround, and needs the `text-encoding` polyfill imported above for TextEncoder.
      forceBinaryWSFrames: true,
      appendMissingNULLonIncoming: true,
      // Re-read the token on every (re)connect attempt, not once at construction time —
      // a token refreshed between connects must be picked up, otherwise a stale token
      // gets rejected by StompAuthChannelInterceptor on the next reconnect.
      beforeConnect: () => {
        const { accessToken } = getAuthState();
        client.connectHeaders = {
          Authorization: accessToken ? `Bearer ${accessToken}` : "",
        };
      },
      onConnect: () => {
        console.log(`[stomp] connected, subscribing /topic/trip/${rideId}`);
        this.lastError = null;
        client.subscribe(`/topic/trip/${rideId}`, (message: IMessage) => {
          try {
            const event = JSON.parse(message.body) as DriverLocationUpdatedEvent;
            onLocation(event);
          } catch (e) {
            // Malformed payload — drop it, don't crash the socket handler.
          }
        });
      },
      // Without these, every connection failure (bad URL, network unreachable, auth
      // rejection, protocol error) was completely silent — the UI only ever saw
      // connected: false with no indication of why, making this impossible to debug.
      onWebSocketError: (event) => {
        this.lastError = `WebSocket error: ${String((event as any)?.message ?? event)}`;
        console.warn(`[stomp] ${this.lastError} (url=${WS_URL})`);
      },
      onWebSocketClose: (event) => {
        console.warn(`[stomp] socket closed code=${event.code} reason=${event.reason}`);
      },
      onStompError: (frame) => {
        this.lastError = `STOMP error: ${frame.headers?.message ?? frame.body}`;
        console.warn(`[stomp] ${this.lastError}`);
      },
    });

    this.client = client;
    client.activate();
  }

  disconnect(): void {
    this.client?.deactivate();
    this.client = null;
    this.currentRideId = null;
  }

  isConnected(): boolean {
    return this.client?.connected ?? false;
  }

  getLastError(): string | null {
    return this.lastError;
  }
}

export const tripStompClient = new TripStompClient();
