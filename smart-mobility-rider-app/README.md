# Smart Mobility Rider App

React Native (Expo, custom dev client) rider app for the Smart Mobility ride-hailing backend, built with Tamagui.

## Setup

```bash
npm install
```

Set environment variables (`.env` or shell) before running:

- `API_BASE_URL` — gateway origin, e.g. `http://localhost:8080`
- `WS_URL` — gateway WebSocket origin, e.g. `ws://localhost:8080/ws`
- `GOOGLE_MAPS_IOS_API_KEY` / `GOOGLE_MAPS_ANDROID_API_KEY` — required for `react-native-maps`; not provided by this backend

## Run

Requires a custom dev client (not Expo Go — `react-native-maps` needs native modules):

```bash
npx expo prebuild
npx expo run:ios      # or run:android
```

## Test

```bash
npm test        # Jest + React Native Testing Library
npm run typecheck
```

## Known backend-dependent gaps

- No "list my rides" endpoint exists yet — `RideHistoryScreen` is an honest empty state, not wired to a stub.
- No rate-driver endpoint exists — no rating UI beyond the read-only aggregate on the profile screen.
- Pickup/drop address entry has no real geocoding — `PickupDropScreen` only offers saved-location quick-picks until a Maps/Places API key is wired in.
- Ride status is polling-only (`GET /rides/{rideId}`) — the STOMP `/topic/trip/{rideId}` channel carries driver location only, never status changes.
