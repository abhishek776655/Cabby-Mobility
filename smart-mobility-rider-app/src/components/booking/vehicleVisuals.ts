import type { ComponentProps } from "react";
import { Ionicons } from "@expo/vector-icons";

type IoniconName = ComponentProps<typeof Ionicons>["name"];

interface VehicleVisual {
  icon: IoniconName;
  label: string;
  blurb: string;
}

const VEHICLE_VISUALS: Record<string, VehicleVisual> = {
  STANDARD: { icon: "car-sport-outline", label: "Standard", blurb: "Affordable everyday rides" },
  PREMIUM: { icon: "car-outline", label: "Premium", blurb: "Newer cars, extra comfort" },
  XL: { icon: "bus-outline", label: "XL", blurb: "Extra room for groups" },
};

const DEFAULT_VISUAL: VehicleVisual = {
  icon: "car-sport-outline",
  label: "Ride",
  blurb: "",
};

export function getVehicleVisual(vehicleType: string): VehicleVisual {
  return VEHICLE_VISUALS[vehicleType] ?? { ...DEFAULT_VISUAL, label: vehicleType };
}
