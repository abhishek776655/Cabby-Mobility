import { env } from "@/lib/env";

export const API_BASE_URL = env.apiBaseUrl;
export const WS_URL = env.wsUrl;

export const RIDE_POLL_INTERVAL_MS = 6000;
export const STOMP_RECONNECT_DELAY_MS = 5000;
export const STOMP_HEARTBEAT_MS = 10000;
export const RATE_LIMIT_RETRY_DELAY_MS = 1500;
