import { useEffect, useState } from "react";
import * as Location from "expo-location";

export function useLocationPermission(): { granted: boolean; requesting: boolean } {
  const [granted, setGranted] = useState(false);
  const [requesting, setRequesting] = useState(true);

  useEffect(() => {
    (async () => {
      const { status } = await Location.requestForegroundPermissionsAsync();
      setGranted(status === "granted");
      setRequesting(false);
    })();
  }, []);

  return { granted, requesting };
}
