const dateFmt = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" });
const shortFmt = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short" });

/** Parses a yyyy-mm-dd date as a local calendar date (no timezone shift). */
export function parseDate(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

export function formatDateRange(start: string, end: string): string {
  const s = parseDate(start);
  const e = parseDate(end);
  if (start === end) return dateFmt.format(s);
  return `${s.getFullYear() === e.getFullYear() ? shortFmt.format(s) : dateFmt.format(s)} – ${dateFmt.format(e)}`;
}

export function formatMoney(amount: number | null | undefined, currency: string): string {
  if (amount == null) return "—";
  try {
    return new Intl.NumberFormat("en-IN", { style: "currency", currency, maximumFractionDigits: 0 }).format(amount);
  } catch {
    return `${currency} ${amount}`;
  }
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]!.toUpperCase())
    .join("");
}

/** Chart-style coordinates, e.g. 15°18′ N, 74°06′ E. */
export function formatCoords(lat: number, lng: number): string {
  const part = (v: number, pos: string, neg: string) => {
    const abs = Math.abs(v);
    let deg = Math.floor(abs);
    let min = Math.round((abs - deg) * 60);
    if (min === 60) {
      deg += 1;
      min = 0;
    }
    return `${deg}°${String(min).padStart(2, "0")}′ ${v >= 0 ? pos : neg}`;
  };
  return `${part(lat, "N", "S")}, ${part(lng, "E", "W")}`;
}

/** Great-circle distance in kilometres. */
export function distanceKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const rad = Math.PI / 180;
  const dLat = (lat2 - lat1) * rad;
  const dLng = (lng2 - lng1) * rad;
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * 6371.0088 * Math.asin(Math.min(1, Math.sqrt(a)));
}

export function formatDistance(km: number): string {
  if (km < 1) return `${Math.round(km * 1000 / 10) * 10} m`;
  return `${km < 10 ? km.toFixed(1) : Math.round(km)} km`;
}

const relativeFmt = new Intl.RelativeTimeFormat("en", { numeric: "auto" });

/** "5 min ago", "yesterday", or a date for anything older than a week. */
export function formatRelative(iso: string, now = Date.now()): string {
  const seconds = Math.round((new Date(iso).getTime() - now) / 1000);
  const abs = Math.abs(seconds);
  if (abs < 60) return "just now";
  if (abs < 3600) return relativeFmt.format(Math.round(seconds / 60), "minute");
  if (abs < 86400) return relativeFmt.format(Math.round(seconds / 3600), "hour");
  if (abs < 7 * 86400) return relativeFmt.format(Math.round(seconds / 86400), "day");
  return dateFmt.format(new Date(iso));
}
