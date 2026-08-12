import { useRecentSearchesStore } from "@/store/recentSearchesStore";

const place = (label: string, latitude: number, longitude: number, address = label) => ({
  label,
  address,
  latitude,
  longitude,
});

describe("recentSearchesStore", () => {
  beforeEach(() => {
    useRecentSearchesStore.setState({ recents: [] });
  });

  it("keeps the most recent pick first", () => {
    const { remember } = useRecentSearchesStore.getState();
    remember(place("India Gate", 28.6129, 77.2295));
    remember(place("Qutub Minar", 28.5245, 77.1855));

    expect(useRecentSearchesStore.getState().recents.map((r) => r.label)).toEqual([
      "Qutub Minar",
      "India Gate",
    ]);
  });

  it("does not duplicate the same address picked twice", () => {
    const { remember } = useRecentSearchesStore.getState();
    remember(place("India Gate", 28.6129, 77.2295));
    remember(place("India Gate", 28.6129, 77.2295));

    expect(useRecentSearchesStore.getState().recents).toHaveLength(1);
  });

  it("collapses the same spot reached with different text", () => {
    const { remember } = useRecentSearchesStore.getState();
    // Same place via search, then via a dropped pin a few metres away.
    remember(place("India Gate", 28.6129, 77.2295, "India Gate, Shahjahan Road"));
    remember(place("Dropped pin", 28.61295, 77.22955, "Dropped pin"));

    expect(useRecentSearchesStore.getState().recents).toHaveLength(1);
    expect(useRecentSearchesStore.getState().recents[0].label).toBe("Dropped pin");
  });

  it("keeps genuinely different places apart", () => {
    const { remember } = useRecentSearchesStore.getState();
    remember(place("India Gate", 28.6129, 77.2295));
    remember(place("Qutub Minar", 28.5245, 77.1855));

    expect(useRecentSearchesStore.getState().recents).toHaveLength(2);
  });

  it("never remembers the rider's live position", () => {
    const { remember } = useRecentSearchesStore.getState();
    // Each GPS fix lands slightly differently; without this guard the list fills with rows
    // that all read "Current location".
    remember(place("Current location", 28.6315, 77.2167, "Your current location"));
    remember(place("Current location", 28.63151, 77.21671, "Your current location"));

    expect(useRecentSearchesStore.getState().recents).toHaveLength(0);
  });

  it("caps the list so it cannot grow without bound", () => {
    const { remember } = useRecentSearchesStore.getState();
    for (let i = 0; i < 12; i += 1) {
      // Spaced far enough apart not to be collapsed by the proximity check.
      remember(place(`Place ${i}`, 28.5 + i * 0.05, 77.1 + i * 0.05));
    }

    expect(useRecentSearchesStore.getState().recents).toHaveLength(6);
    expect(useRecentSearchesStore.getState().recents[0].label).toBe("Place 11");
  });
});
