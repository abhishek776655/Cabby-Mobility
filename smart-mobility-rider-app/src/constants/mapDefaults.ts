// Camera fallback for the booking map before the rider's own position is known.
//
// This is ONLY ever a map viewport, never an address: it must not become a pickup or drop.
// The screens previously fell back to fixed "test" coordinates for those too, which silently
// set a rider's pickup to a place they'd never chosen whenever GPS was denied or failed.
//
// Must stay inside the routing-service's Valhalla tile coverage (docker/docker-compose.yml
// loads NewDelhi.osm.pbf), so the initial view matches the region we can actually route.
export const MAP_DEFAULT_CENTER = {
  latitude: 28.6315,
  longitude: 77.2167,
} as const;

export const MAP_DEFAULT_DELTA = {
  latitudeDelta: 0.05,
  longitudeDelta: 0.05,
} as const;
