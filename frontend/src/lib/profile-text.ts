// Shared wording for quiz and profile screens.

export const RATING_LABELS = ["Not for me", "Meh", "Sometimes", "Love it", "Can't skip"];

export function crowdText(v: number) {
  if (v <= 20) return "Avoid crowds";
  if (v <= 45) return "Prefer quiet";
  if (v <= 70) return "Don't mind some";
  return "Crowds are fine";
}

export function gemsText(v: number) {
  if (v <= 25) return "The classics";
  if (v <= 45) return "Mostly classics";
  if (v <= 60) return "A mix";
  if (v <= 80) return "Mostly hidden gems";
  return "Hidden gems only";
}

export function weightText(w: number) {
  if (w >= 0.85) return "Can't skip";
  if (w >= 0.65) return "Love it";
  if (w >= 0.4) return "Sometimes";
  if (w > 0.1) return "Meh";
  return "Not for me";
}
