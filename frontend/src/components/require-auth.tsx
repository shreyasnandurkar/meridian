"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";

import { useAuth } from "@/lib/auth";

/** Client-side guard: sends anonymous visitors to /login and back afterwards. The API enforces auth regardless. */
export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { status } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (status === "anonymous") router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [status, router, pathname]);

  if (status !== "authenticated") {
    return <PageLoading />;
  }
  return <>{children}</>;
}

export function PageLoading({ label = "Loading…" }: { label?: string }) {
  return (
    <div role="status" className="text-muted-foreground flex items-center gap-2 py-24 text-sm">
      <Loader2 className="size-4 animate-spin" aria-hidden />
      {label}
    </div>
  );
}
