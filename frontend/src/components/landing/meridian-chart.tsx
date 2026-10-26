/**
 * Landing hero illustration: four travelers with different wishes, each route drawn once toward a single meeting
 * point on the meridian. The only page-load motion on the site; disabled under prefers-reduced-motion.
 */
const MEET = { x: 262, y: 226 };

const TRAVELERS = [
  { name: "Asha", wish: "slow mornings, quiet beaches", x: 72, y: 84, lx: 86, ly: 80, anchor: "start" },
  { name: "Ben", wish: "night markets, live music", x: 438, y: 70, lx: 424, ly: 66, anchor: "end" },
  { name: "Chen", wish: "keeps it under ₹15,000", x: 60, y: 352, lx: 74, ly: 370, anchor: "start" },
  { name: "Dev", wish: "old churches, no crowds", x: 446, y: 372, lx: 432, ly: 392, anchor: "end" },
] as const;

function route(x: number, y: number) {
  const cx = (x + MEET.x) / 2 + (y < MEET.y ? 30 : -30);
  const cy = (y + MEET.y) / 2 + (x < MEET.x ? 24 : -24);
  return `M${x},${y} Q${cx},${cy} ${MEET.x},${MEET.y}`;
}

export function MeridianChart() {
  return (
    <figure className="relative">
      <svg
        viewBox="0 0 500 440"
        className="text-foreground h-auto w-full"
        role="img"
        aria-labelledby="chart-title chart-desc"
      >
        <title id="chart-title">Four travelers, one plan</title>
        <desc id="chart-desc">
          Asha, Ben, Chen and Dev each want something different. Their routes meet at one point on the meridian: the
          trip they agree on.
        </desc>
        <defs>
          <pattern id="grid" width="50" height="50" patternUnits="userSpaceOnUse">
            <path d="M50 0H0V50" fill="none" stroke="var(--gridline)" strokeWidth="1" />
          </pattern>
        </defs>
        <rect width="500" height="440" fill="url(#grid)" />

        {/* depth contours around the meeting point */}
        <g fill="none" stroke="var(--contour)" strokeWidth="1" opacity="0.55">
          <ellipse cx={MEET.x} cy={MEET.y} rx="54" ry="40" transform={`rotate(-12 ${MEET.x} ${MEET.y})`} />
          <ellipse cx={MEET.x + 6} cy={MEET.y + 4} rx="104" ry="78" transform={`rotate(-8 ${MEET.x} ${MEET.y})`} />
          <ellipse cx={MEET.x + 14} cy={MEET.y + 8} rx="168" ry="128" transform={`rotate(-4 ${MEET.x} ${MEET.y})`} opacity="0.6" />
        </g>

        {/* the meridian */}
        <line
          x1={MEET.x}
          y1="8"
          x2={MEET.x}
          y2="432"
          stroke="var(--signal)"
          strokeWidth="1.5"
          style={{ strokeDasharray: 424, ["--len" as string]: 424, animation: "draw-line 900ms ease-out both" }}
        />
        <text x={MEET.x + 8} y="22" className="figures" fontSize="11" fill="var(--signal)">
          74°07′ E
        </text>

        {/* routes */}
        <g fill="none" stroke="currentColor" strokeWidth="1.25" strokeLinecap="round" opacity="0.7">
          {TRAVELERS.map((t, i) => (
            <path
              key={t.name}
              d={route(t.x, t.y)}
              pathLength={1}
              style={{
                strokeDasharray: 1,
                ["--len" as string]: 1,
                animation: `draw-line 1100ms cubic-bezier(.3,.6,.2,1) ${500 + i * 140}ms both`,
              }}
            />
          ))}
        </g>

        {/* travelers */}
        {TRAVELERS.map((t) => (
          <g key={t.name}>
            <circle cx={t.x} cy={t.y} r="5.5" fill="var(--paper-raised)" stroke="currentColor" strokeWidth="1.75" />
            <text x={t.lx} y={t.ly} textAnchor={t.anchor} fontSize="14" fontWeight="600" fill="currentColor">
              {t.name}
            </text>
            <text x={t.lx} y={t.ly + 17} textAnchor={t.anchor} fontSize="12" fill="var(--ink-soft)">
              {t.wish}
            </text>
          </g>
        ))}

        {/* meeting point */}
        <g style={{ animation: "fade-in 500ms ease-out 1450ms both" }}>
          <circle cx={MEET.x} cy={MEET.y} r="13" fill="var(--signal)" opacity="0.14" />
          <circle cx={MEET.x} cy={MEET.y} r="5" fill="var(--signal)" />
          <text x={MEET.x + 16} y={MEET.y - 10} className="place" fontSize="19" fill="currentColor">
            Goa
          </text>
          <text x={MEET.x + 16} y={MEET.y + 8} fontSize="12" fill="var(--ink-soft)">
            5 days, agreed by all four
          </text>
        </g>
      </svg>
    </figure>
  );
}
