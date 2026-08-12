import { shortName } from "@/screens/booking/FareCompareScreen";

describe("shortName", () => {
  it("leads with the house number and block for a residential address", () => {
    expect(shortName("A-115, Block-A, Street Number 2, Haritnagar")).toBe("A-115, Block-A");
  });

  it("keeps a bare house number", () => {
    expect(shortName("42, Rajpath, New Delhi")).toBe("42");
  });

  it("keeps a sector number", () => {
    expect(shortName("Rohini Sector 7, North West Delhi")).toBe("Rohini Sector 7");
  });

  it("falls back to the first words for a named place", () => {
    expect(shortName("Qutub Minar Complex, Baba Shrichand Marg, South Delhi")).toBe("Qutub Minar");
  });

  it("handles a single-segment address", () => {
    expect(shortName("Connaught Place")).toBe("Connaught Place");
  });

  it("returns the input unchanged when there is nothing to condense", () => {
    expect(shortName("")).toBe("");
  });
});
