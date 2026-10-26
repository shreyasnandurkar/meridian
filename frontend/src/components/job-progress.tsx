"use client";

import { Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import type { AiJob } from "@/lib/types";

/** Progress for an async AI job, with cancel. Announced politely to screen readers. */
export function JobProgress({
  job,
  onCancel,
  cancelling,
}: {
  job: AiJob | undefined;
  onCancel?: () => void;
  cancelling?: boolean;
}) {
  const percent = job?.percent ?? 0;
  return (
    <div className="space-y-3" role="status" aria-live="polite">
      <div className="flex items-center gap-2 text-sm">
        <Loader2 className="text-signal size-4 animate-spin" aria-hidden />
        <span>{job?.message ?? "Starting…"}</span>
        <span className="text-muted-foreground ml-auto tabular-nums">{percent}%</span>
      </div>
      <Progress value={percent} aria-label="AI job progress" />
      {onCancel && (
        <Button variant="outline" size="sm" onClick={onCancel} disabled={cancelling}>
          Cancel
        </Button>
      )}
    </div>
  );
}
