import Link from "next/link";

import { MeridianChart } from "@/components/landing/meridian-chart";
import { Button } from "@/components/ui/button";

/** Chart-legend symbols: each one is reused in the product for the thing it stands for. */
const LEGEND = [
  {
    symbol: (
      <svg viewBox="0 0 28 28" className="size-7" aria-hidden>
        <circle cx="14" cy="14" r="5" fill="var(--paper-raised)" stroke="currentColor" strokeWidth="1.75" />
      </svg>
    ),
    title: "Everyone says what they want",
    body: "Each traveler takes a two-minute quiz about pace, food, crowds and budget. Budgets stay private; the group sees only a shared range.",
  },
  {
    symbol: (
      <svg viewBox="0 0 28 28" className="size-7" aria-hidden fill="none" stroke="currentColor" strokeWidth="1.5">
        <circle cx="11" cy="14" r="7" />
        <circle cx="17" cy="14" r="7" />
      </svg>
    ),
    title: "See where the group agrees",
    body: "Group fit lays out overlaps and clashes in plain words before anyone books anything.",
  },
  {
    symbol: (
      <svg viewBox="0 0 28 28" className="size-7" aria-hidden fill="none" stroke="currentColor" strokeWidth="1.5">
        <path d="M14 5v18M9 10l5-5 5 5" />
        <circle cx="14" cy="21" r="2" fill="currentColor" />
      </svg>
    ),
    title: "Places locals use, with reasons",
    body: "Suggestions lean away from crowded tourist traps, and every one says why it fits your group.",
  },
  {
    symbol: (
      <svg viewBox="0 0 28 28" className="size-7" aria-hidden fill="none" stroke="currentColor" strokeWidth="1.5">
        <rect x="6" y="6" width="16" height="16" rx="2" />
        <path d="M6 14h16" strokeDasharray="2 2" />
      </svg>
    ),
    title: "Nobody pays to be ranked",
    body: "Ranking never uses commercial signals. Every price shows where it came from and when it was checked.",
  },
  {
    symbol: (
      <svg viewBox="0 0 28 28" className="size-7" aria-hidden>
        <path d="M14 3v22" stroke="var(--signal)" strokeWidth="2" strokeLinecap="round" />
        <circle cx="14" cy="14" r="3.5" fill="var(--signal)" />
      </svg>
    ),
    title: "Decide together, on the record",
    body: "The plan locks only when the group has voted, with an equal vote each. Every decision is written down.",
  },
];

export default function Home() {
  return (
    <div>
      <section className="grid items-center gap-10 pt-12 pb-16 sm:pt-16 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.05fr)] lg:gap-16 lg:pt-20">
        <div className="max-w-xl">
          <h1 className="text-display font-semibold">Plan one trip together, without the group chat.</h1>
          <p className="text-muted-foreground mt-6 max-w-[34rem] text-lg">
            Meridian learns what each of you wants from the trip, shows where you agree and where you don&apos;t, and
            drafts a plan the whole group votes on before it&apos;s locked.
          </p>
          <div className="mt-9 flex flex-wrap gap-3">
            <Button size="lg" asChild>
              <Link href="/signup">Start a trip</Link>
            </Button>
            <Button size="lg" variant="outline" asChild>
              <Link href="/login">Log in</Link>
            </Button>
          </div>
        </div>
        <div className="bg-card -mx-4 border-y px-2 py-2 sm:mx-0 sm:rounded-xl sm:border sm:px-4 sm:py-4">
          <MeridianChart />
        </div>
      </section>

      <section aria-labelledby="legend-h" className="border-t pt-12">
        <h2 id="legend-h" className="text-2xl font-semibold">
          How it works
        </h2>
        <dl className="mt-8 grid gap-x-12 gap-y-9 sm:grid-cols-2 lg:grid-cols-3">
          {LEGEND.map((item) => (
            <div key={item.title} className="grid grid-cols-[2.25rem_1fr] gap-x-3">
              <span className="text-foreground pt-0.5">{item.symbol}</span>
              <div>
                <dt className="font-semibold">{item.title}</dt>
                <dd className="text-muted-foreground mt-1.5 text-[0.95rem] leading-relaxed">{item.body}</dd>
              </div>
            </div>
          ))}
        </dl>
      </section>
    </div>
  );
}
