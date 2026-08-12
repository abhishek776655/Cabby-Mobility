// Shared design tokens — "Clean & trustworthy" (restrained neutral palette + one accent).
// Canonical source; authTheme.ts re-exports these under its old names so existing auth
// screens keep working unchanged.
export const colors = {
  bg: "#FAFAF9",
  surface: "#FFFFFF",
  surfaceSunken: "#F4F4F3",
  ink: "#18181B",
  inkMuted: "#52525B",
  inkFaint: "#A1A1AA",
  border: "#E4E4E7",
  borderFocus: "#4F46E5",
  primary: "#4F46E5",
  primaryPressed: "#4338CA",
  primarySoft: "#EEF2FF",
  primaryText: "#FFFFFF",
  accent: "#059669",
  accentSoft: "#ECFDF5",
  danger: "#DC2626",
  dangerBg: "#FEF2F2",
  dangerBorder: "#FECACA",
  warning: "#D97706",
  warningBg: "#FFFBEB",
  overlay: "rgba(24,24,27,0.45)",
} as const;

export const spacing = {
  xs: 6,
  sm: 12,
  md: 20,
  lg: 32,
  xl: 48,
} as const;

export const radii = {
  sm: 10,
  md: 14,
  lg: 20,
  pill: 999,
} as const;
