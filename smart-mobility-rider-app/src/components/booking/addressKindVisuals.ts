import type { ComponentProps } from "react";
import { Ionicons } from "@expo/vector-icons";

type IoniconName = ComponentProps<typeof Ionicons>["name"];

/**
 * Photon returns the raw OSM value for a place ("monument", "suburb", "station", ...). Mapping
 * it to a drawn icon lets a rider tell a metro stop from a neighbourhood without reading both
 * lines of every row — the whole point of a scannable result list.
 */
const KIND_ICONS: Record<string, IoniconName> = {
  // transport
  station: "train-outline",
  halt: "train-outline",
  bus_stop: "bus-outline",
  bus_station: "bus-outline",
  aerodrome: "airplane-outline",
  terminal: "airplane-outline",
  subway: "subway-outline",
  // areas
  suburb: "map-outline",
  neighbourhood: "map-outline",
  quarter: "map-outline",
  city: "business-outline",
  town: "business-outline",
  village: "business-outline",
  residential: "home-outline",
  // notable places
  monument: "flag-outline",
  attraction: "star-outline",
  museum: "star-outline",
  park: "leaf-outline",
  hospital: "medkit-outline",
  school: "school-outline",
  college: "school-outline",
  university: "school-outline",
  mall: "storefront-outline",
  supermarket: "storefront-outline",
  restaurant: "restaurant-outline",
  cafe: "cafe-outline",
  hotel: "bed-outline",
  police: "shield-outline",
  fuel: "car-outline",
};

const DEFAULT_ICON: IoniconName = "location-outline";

export function getAddressKindIcon(kind: string | null | undefined): IoniconName {
  if (!kind) return DEFAULT_ICON;
  return KIND_ICONS[kind] ?? DEFAULT_ICON;
}
