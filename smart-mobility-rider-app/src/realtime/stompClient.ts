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

  connect(rideId: string, onLocation: (event: DriverLocationUpdatedEvent) => void): void {
    if (this.client?.active && this.currentRideId === rideId) {
      return;
    }
    this.disconnect();
    this.currentRideId = rideId;

    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: STOMP_RECONNECT_DELAY_MS,
      heartbeatIncoming: STOMP_HEARTBEAT_MS,
      heartbeatOutgoing: STOMP_HEARTBEAT_MS,
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
        client.subscribe(`/topic/trip/${rideId}`, (message: IMessage) => {
          try {
            const event = JSON.parse(message.body) as DriverLocationUpdatedEvent;
            onLocation(event);
          } catch (e) {
            // Malformed payload — drop it, don't crash the socket handler.
          }
        });
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
}

export const tripStompClient = new TripStompClient();
