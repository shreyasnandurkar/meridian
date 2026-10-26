import Link from "next/link";
import { BadgeCheck, Scale, Sparkles, Users } from "lucide-react";

import { Button } from "@/components/ui/button";

const pillars = [
  {
    icon: Users,
    title: "One shared space",
    body: "Everyone records their own preferences. No more chasing replies across chats and tabs.",
  },
  {
    icon: Scale,
    title: "Group fit, made visible",
    body: "See where budgets, pace and interests agree or clash before anyone books anything.",
  },
  {
    icon: Sparkles,
    title: "Hidden gems, not traps",
    body: "Suggestions favour locally rooted places that match real interests, and each one says why it was picked.",
  },
  {
    icon: BadgeCheck,
    title: "No paid placement",
    body: "Ranking never uses commercial signals. Every price shows its source and when it was checked.",
  },
];

export default function Home() {
  return (
    <div className="py-16 sm:py-24">
      <section className="max-w-2xl">
        <p className="text-primary text-sm font-medium">Collaborative trip planning</p>
        <h1 className="mt-3 text-4xl font-semibold tracking-tight text-balance sm:text-5xl">
          Plan one trip, together, without the group-chat chaos.
        </h1>
        <p className="text-muted-foreground mt-5 text-lg text-pretty">
          Meridian learns each traveler&apos;s style, reconciles the group&apos;s preferences and drafts an itinerary
          the whole group votes on before it&apos;s locked.
        </p>
        <div className="mt-8 flex flex-wrap gap-3">
          <Button size="lg" asChild>
            <Link href="/signup">Start planning</Link>
          </Button>
          <Button size="lg" variant="outline" asChild>
            <Link href="/login">I have an account</Link>
          </Button>
        </div>
      </section>

      <section aria-label="Why Meridian" className="mt-20 grid gap-4 sm:grid-cols-2">
        {pillars.map(({ icon: Icon, title, body }) => (
          <div key={title} className="bg-card rounded-xl border p-6">
            <Icon className="text-primary size-5" aria-hidden />
            <h2 className="mt-3 font-semibold">{title}</h2>
            <p className="text-muted-foreground mt-1.5 text-sm">{body}</p>
          </div>
        ))}
      </section>
    </div>
  );
}
