import { AlertTriangle } from "lucide-react";

import { Button } from "@/components/ui/button";

export function ErrorState({
  title = "Something went wrong",
  error,
  onRetry,
}: {
  title?: string;
  error?: unknown;
  onRetry?: () => void;
}) {
  const message = error instanceof Error ? error.message : undefined;
  return (
    <div role="alert" className="flex flex-col items-start gap-3 rounded-xl border border-destructive/30 p-6">
      <div className="flex items-center gap-2 font-medium">
        <AlertTriangle className="text-destructive size-4" aria-hidden />
        {title}
      </div>
      {message && <p className="text-muted-foreground text-sm">{message}</p>}
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}
