// Scoped design tokens for the auth flow (Login/Register). Not wired into tamagui.config.ts —
// every other screen still uses Tamagui's default theme; this is a deliberately narrow surface
// redesign, not a global rebrand.
export const authColors = {
  bg: "#FAFAF9",
  surface: "#FFFFFF",
  ink: "#18181B",
  inkMuted: "#52525B",
  inkFaint: "#A1A1AA",
  border: "#E4E4E7",
  borderFocus: "#4F46E5",
  primary: "#4F46E5",
  primaryPressed: "#4338CA",
  primaryText: "#FFFFFF",
  danger: "#DC2626",
  dangerBg: "#FEF2F2",
  dangerBorder: "#FECACA",
} as const;

export const authSpacing = {
  xs: 6,
  sm: 12,
  md: 20,
  lg: 32,
  xl: 48,
} as const;
