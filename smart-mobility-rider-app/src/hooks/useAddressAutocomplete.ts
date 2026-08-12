import { useEffect, useRef, useState } from "react";
import { autocompleteAddress } from "@/api/geocode";
import type { GeocodeSuggestion } from "@/api/types";

const DEBOUNCE_MS = 250;
/** Matches routing-service's MIN_QUERY_LENGTH — no point spending a round trip it will reject. */
const MIN_QUERY_LENGTH = 3;

interface Options {
  /** Bias results toward the rider's position when known. */
  lat?: number;
  lon?: number;
}

interface Result {
  suggestions: GeocodeSuggestion[];
  loading: boolean;
  error: string | null;
}

/**
 * Debounced address autocomplete.
 *
 * Every keystroke supersedes the one before it, so in-flight requests are aborted rather than
 * left to land later — otherwise a slow early response can overwrite the results for a longer,
 * more specific query the rider has already typed.
 */
export function useAddressAutocomplete(query: string, { lat, lon }: Options = {}): Result {
  const [suggestions, setSuggestions] = useState<GeocodeSuggestion[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    const trimmed = query.trim();

    abortRef.current?.abort();

    if (trimmed.length < MIN_QUERY_LENGTH) {
      setSuggestions([]);
      setLoading(false);
      setError(null);
      return;
    }

    setLoading(true);
    const controller = new AbortController();
    abortRef.current = controller;

    const timer = setTimeout(() => {
      autocompleteAddress({ query: trimmed, lat, lon, signal: controller.signal })
        .then((results) => {
          if (controller.signal.aborted) return;
          setSuggestions(results);
          setError(null);
        })
        .catch((e: unknown) => {
          if (controller.signal.aborted) return;
          // An aborted request is the normal path here (the rider kept typing), not a failure
          // worth surfacing — only real errors should reach the UI.
          const name = (e as { name?: string } | null)?.name;
          const code = (e as { code?: string } | null)?.code;
          if (name === "CanceledError" || name === "AbortError" || code === "ERR_CANCELED") return;
          setSuggestions([]);
          setError("Couldn't search addresses");
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
    }, DEBOUNCE_MS);

    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [query, lat, lon]);

  return { suggestions, loading, error };
}
