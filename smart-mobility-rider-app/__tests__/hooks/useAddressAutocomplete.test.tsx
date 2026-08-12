import React from "react";
import { Text } from "react-native";
import { render, screen, waitFor } from "@testing-library/react-native";
import { useAddressAutocomplete } from "@/hooks/useAddressAutocomplete";
import * as geocodeApi from "@/api/geocode";

jest.mock("@/api/geocode");

const suggestion = (label: string) => ({
  label,
  description: "New Delhi, Delhi",
  lat: 28.6,
  lng: 77.2,
  kind: "suburb",
});

/**
 * Driven through a host component rather than RNTL's renderHook: on this project's
 * RNTL 14 / React 19 pairing renderHook returns an empty object, so its `result` handle is
 * unusable. `render` works, so the hook is exercised the same way the app exercises it.
 */
function Probe({ query }: { query: string }) {
  const { suggestions, loading, error } = useAddressAutocomplete(query);
  return (
    <>
      <Text testID="labels">{suggestions.map((s) => s.label).join("|")}</Text>
      <Text testID="loading">{loading ? "loading" : "idle"}</Text>
      <Text testID="error">{error ?? "none"}</Text>
    </>
  );
}

/**
 * Types a sequence of queries in quick succession from inside the component. Driving it here
 * rather than via rerender() keeps the test independent of render()'s return handle, which is
 * empty on this RNTL/React pairing.
 */
function TypingProbe({ queries }: { queries: string[] }) {
  const [index, setIndex] = React.useState(0);
  React.useEffect(() => {
    if (index >= queries.length - 1) return;
    const timer = setTimeout(() => setIndex((i) => i + 1), 20);
    return () => clearTimeout(timer);
  }, [index, queries.length]);
  return <Probe query={queries[index]} />;
}

describe("useAddressAutocomplete", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (geocodeApi.autocompleteAddress as jest.Mock).mockResolvedValue([]);
  });

  it("does not call the API below the minimum query length", async () => {
    render(<Probe query="co" />);

    await new Promise((resolve) => setTimeout(resolve, 400));

    expect(geocodeApi.autocompleteAddress).not.toHaveBeenCalled();
    expect(screen.getByTestId("labels")).toHaveTextContent("");
  });

  it("returns suggestions once the query is long enough", async () => {
    (geocodeApi.autocompleteAddress as jest.Mock).mockResolvedValue([suggestion("Connaught Place")]);

    render(<Probe query="connaught" />);

    await waitFor(() => expect(screen.getByTestId("labels")).toHaveTextContent("Connaught Place"));
    expect(geocodeApi.autocompleteAddress).toHaveBeenCalledTimes(1);
  });

  it("debounces rapid typing into a single request for the final query", async () => {
    (geocodeApi.autocompleteAddress as jest.Mock).mockResolvedValue([suggestion("Qutub Minar")]);

    render(<TypingProbe queries={["qut", "qutu", "qutub"]} />);

    await waitFor(() => expect(screen.getByTestId("labels")).toHaveTextContent("Qutub Minar"));
    expect(geocodeApi.autocompleteAddress).toHaveBeenCalledTimes(1);
    expect(geocodeApi.autocompleteAddress).toHaveBeenCalledWith(
      expect.objectContaining({ query: "qutub" })
    );
  });

  it("surfaces a message when the lookup fails", async () => {
    (geocodeApi.autocompleteAddress as jest.Mock).mockRejectedValue(new Error("boom"));

    render(<Probe query="connaught" />);

    await waitFor(() =>
      expect(screen.getByTestId("error")).toHaveTextContent("Couldn't search addresses")
    );
  });

  it("treats an aborted request as normal typing, not an error", async () => {
    const abortError = Object.assign(new Error("canceled"), { name: "CanceledError" });
    (geocodeApi.autocompleteAddress as jest.Mock).mockRejectedValue(abortError);

    render(<Probe query="connaught" />);

    await waitFor(() => expect(geocodeApi.autocompleteAddress).toHaveBeenCalled());
    await new Promise((resolve) => setTimeout(resolve, 60));

    // The rider kept typing; a cancelled in-flight request must not surface as a failure.
    expect(screen.getByTestId("error")).toHaveTextContent("none");
  });
});
