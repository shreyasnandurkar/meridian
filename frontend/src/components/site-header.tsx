"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";

import { BrandMark } from "@/components/brand-mark";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/lib/auth";
import { cn } from "@/lib/utils";

export function SiteHeader() {
  const { user, status, logout } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  const navLink = (href: string, label: string) => {
    const active = pathname === href || (href === "/trips" && pathname.startsWith("/trips"));
    return (
      <Link
        href={href}
        aria-current={active ? "page" : undefined}
        className={cn(
          "focus-visible:ring-ring/60 rounded-sm px-2 py-1 text-sm transition-colors outline-none focus-visible:ring-2",
          active ? "text-foreground font-medium" : "text-muted-foreground hover:text-foreground",
        )}
      >
        {label}
      </Link>
    );
  };

  return (
    <header className="bg-background/85 supports-[backdrop-filter]:bg-background/70 sticky top-0 z-40 border-b backdrop-blur">
      <div className="mx-auto flex h-14 w-full max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
        <Link
          href={user ? "/trips" : "/"}
          className="focus-visible:ring-ring/60 flex items-center gap-2 rounded-sm text-[0.95rem] font-semibold tracking-tight outline-none focus-visible:ring-2"
        >
          <BrandMark />
          Meridian
        </Link>
        <nav aria-label="Account" className="flex items-center gap-1 sm:gap-2">
          {status === "authenticated" && user ? (
            <>
              {navLink("/trips", "My trips")}
              {navLink("/profile", "Profile")}
              <span className="bg-border mx-1 hidden h-5 w-px md:block" aria-hidden />
              <span className="text-muted-foreground hidden text-sm md:inline">{user.displayName}</span>
              <Button
                variant="ghost"
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
