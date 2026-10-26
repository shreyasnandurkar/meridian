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
