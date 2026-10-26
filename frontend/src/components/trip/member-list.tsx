import { CheckCircle2, CircleDashed, Crown } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { initials } from "@/lib/format";
import type { Member } from "@/lib/types";

/** Members with their contribution status — icon + text, never colour alone (spec §5.4). */
export function MemberList({ members, currentUserId }: { members: Member[]; currentUserId?: string }) {
  return (
    <ul className="divide-y">
      {members.map((m) => (
        <li key={m.userId} className="flex items-center gap-3 py-3">
          <span
            aria-hidden
            className="bg-secondary text-secondary-foreground flex size-9 shrink-0 items-center justify-center rounded-full text-sm font-medium"
          >
            {initials(m.displayName)}
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate font-medium">
              {m.displayName}
              {m.userId === currentUserId && <span className="text-muted-foreground font-normal"> (you)</span>}
            </p>
            {m.hasProfile ? (
              <p className="text-success inline-flex items-center gap-1 text-xs">
                <CheckCircle2 className="size-3.5" aria-hidden /> Travel style shared
              </p>
            ) : (
              <p className="text-muted-foreground inline-flex items-center gap-1 text-xs">
                <CircleDashed className="size-3.5" aria-hidden /> Hasn&apos;t taken the quiz yet
              </p>
            )}
          </div>
          {m.role === "ORGANIZER" && (
            <Badge variant="secondary">
              <Crown aria-hidden /> Organizer
            </Badge>
          )}
        </li>
      ))}
    </ul>
  );
}
