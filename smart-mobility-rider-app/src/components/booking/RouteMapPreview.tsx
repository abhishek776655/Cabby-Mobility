import React, { useRef } from "react";
import { StyleSheet, View, type DimensionValue } from "react-native";
import MapView, { Marker, Polyline } from "react-native-maps";
import { colors } from "@/constants/theme";
import type { Coordinate } from "@/api/types";

interface Props {
  pickup: { latitude: number; longitude: number };
  drop: { latitude: number; longitude: number };
  routeCoordinates?: Coordinate[];
  height?: DimensionValue;
  /** Full-bleed map screens (e.g. behind a bottom sheet) pass false to drop the corner radius. */
  rounded?: boolean;
  /**
   * 0 = the fitted route view, 1 = fully zoomed in. Driven by the bottom sheet's drag
   * progress so the map stays legible as the sheet shrinks the visible map area — a smaller
   * viewport with the same zoom would clip the route, so we zoom in to compensate.
   */
  zoomProgress?: number;
}

const BASE_ZOOM_BOOST = 2.2;

export function RouteMapPreview({
  pickup,
  drop,
  routeCoordinates,
  height = 240,
  rounded = true,
  zoomProgress = 0,
}: Props) {
  const mapRef = useRef<MapView>(null);
  const baseZoomRef = useRef<number | null>(null);

  const routePoints = (routeCoordinates ?? []).map((c) => ({
    latitude: c.lat,
    longitude: c.lng,
  }));
  const linePoints = routePoints.length > 1 ? routePoints : [pickup, drop];
  const routeKey = `${pickup.latitude},${pickup.longitude},${drop.latitude},${drop.longitude},${routePoints.length}`;

  // Re-fit whenever the actual route changes (new trip picked, quote refetched with real
  // route coordinates) — fitToCoordinates only runs once via onLayout otherwise, so a screen
  // that stays mounted across trips (route.params updated, not remounted) kept showing the
  // old straight line/zoom until something else (the drag gesture) forced a camera update.
  React.useEffect(() => {
    mapRef.current?.fitToCoordinates([pickup, drop, ...routePoints], {
      edgePadding: { top: 40, right: 40, bottom: 40, left: 40 },
      animated: true,
    });
    const timer = setTimeout(() => {
      mapRef.current?.getCamera().then((camera) => {
        baseZoomRef.current = camera.zoom ?? 14;
      });
    }, 350);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [routeKey]);

  React.useEffect(() => {
    if (baseZoomRef.current === null) return;
    mapRef.current?.animateCamera(
      { zoom: baseZoomRef.current + zoomProgress * BASE_ZOOM_BOOST },
      { duration: 0 }
    );
  }, [zoomProgress]);

  return (
    <View style={[styles.container, { height, borderRadius: rounded ? 20 : 0 }]}>
      <MapView ref={mapRef} style={styles.map} scrollEnabled zoomEnabled pitchEnabled rotateEnabled>
        <Polyline coordinates={linePoints} strokeColor={colors.primary} strokeWidth={4} />
        <Marker coordinate={pickup} anchor={{ x: 0.5, y: 0.5 }}>
          <View style={styles.pickupDot} />
        </Marker>
        <Marker coordinate={drop} anchor={{ x: 0.5, y: 1 }}>
          <View style={styles.dropPin}>
            <View style={styles.dropPinInner} />
          </View>
        </Marker>
      </MapView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    overflow: "hidden",
    backgroundColor: colors.surfaceSunken,
  },
  map: {
    flex: 1,
  },
  pickupDot: {
    width: 16,
    height: 16,
    borderRadius: 8,
    backgroundColor: colors.accent,
    borderWidth: 3,
    borderColor: colors.surface,
  },
  dropPin: {
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: colors.ink,
    alignItems: "center",
    justifyContent: "center",
  },
  dropPinInner: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.surface,
  },
});
