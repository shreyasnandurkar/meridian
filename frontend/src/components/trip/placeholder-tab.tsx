import type { LucideIcon } from "lucide-react";

/** Empty state for hub sections that later milestones fill in. */
export function PlaceholderTab({
  icon: Icon,
  title,
  body,
  milestone,
}: {
  icon: LucideIcon;
  title: string;
  body: string;
  milestone: number;
}) {
  return (
    <div className="flex flex-col items-center rounded-xl border border-dashed px-6 py-16 text-center">
      <Icon className="text-muted-foreground size-8" aria-hidden />
      <h2 className="mt-4 font-medium">{title}</h2>
      <p className="text-muted-foreground mt-1 max-w-md text-sm">{body}</p>
      <p className="text-muted-foreground mt-4 text-xs">Coming in milestone {milestone}.</p>
    </div>
  );
}
