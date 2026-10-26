"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { Compass } from "lucide-react";

import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/lib/auth";

export function SiteHeader() {
  const { user, status, logout } = useAuth();
  const router = useRouter();

  return (
    <header className="border-b bg-background/80 backdrop-blur supports-[backdrop-filter]:bg-background/60 sticky top-0 z-40">
      <div className="mx-auto flex h-14 w-full max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
        <Link href={user ? "/trips" : "/"} className="flex items-center gap-2 font-semibold tracking-tight">
          <Compass className="size-5 text-primary" aria-hidden />
          Meridian
        </Link>
        <nav aria-label="Account" className="flex items-center gap-2">
          {status === "authenticated" && user ? (
            <>
              <span className="text-muted-foreground hidden text-sm sm:inline">{user.displayName}</span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  logout();
                  router.push("/");
                }}
              >
                Log out
              </Button>
            </>
          ) : status === "anonymous" ? (
            <>
              <Button variant="ghost" size="sm" asChild>
                <Link href="/login">Log in</Link>
              </Button>
              <Button size="sm" asChild>
                <Link href="/signup">Sign up</Link>
              </Button>
            </>
          ) : null}
          <ThemeToggle />
        </nav>
      </div>
    </header>
  );
}
