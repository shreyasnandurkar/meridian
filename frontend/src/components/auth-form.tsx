"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";

type Mode = "login" | "signup";

export function AuthForm({ mode }: { mode: Mode }) {
  const { login, signup, status } = useAuth();
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const submittedRef = useRef(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    // already signed in when arriving here; a fresh submit navigates itself
    if (status === "authenticated" && !submittedRef.current) router.replace(next);
  }, [status, router, next]);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    const email = String(data.get("email") ?? "");
    const password = String(data.get("password") ?? "");
    setSubmitting(true);
    setError(null);
    setFieldErrors({});
    submittedRef.current = true;
    try {
      if (mode === "signup") await signup(email, password, String(data.get("displayName") ?? ""));
      else await login(email, password);
      // new travelers take the style quiz first, then continue where they were heading
      router.replace(mode === "signup" ? `/onboarding?next=${encodeURIComponent(next)}` : next);
    } catch (err) {
      submittedRef.current = false;
      if (err instanceof ApiError) {
        setError(err.message);
        setFieldErrors(err.problem.errors ?? {});
      } else {
        setError("Something went wrong. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  const isSignup = mode === "signup";
  return (
    <div className="mx-auto mt-12 w-full max-w-sm sm:mt-20">
      <div className="border-signal border-l-2 pl-4">
        <h1 className="text-2xl font-semibold">{isSignup ? "Create your account" : "Welcome back"}</h1>
        <p className="text-muted-foreground mt-1 text-sm">
          {isSignup ? "Start a trip and invite your group." : "Log in to see your trips."}
        </p>
      </div>
    <Card className="mt-6">
      <form onSubmit={onSubmit} noValidate>
        <CardContent className="grid gap-4 pt-6">
          {isSignup && (
            <Field id="displayName" label="Your name" autoComplete="name" error={fieldErrors.displayName} />
          )}
          <Field id="email" label="Email" type="email" autoComplete="email" error={fieldErrors.email} />
          <Field
            id="password"
            label="Password"
            type="password"
            autoComplete={isSignup ? "new-password" : "current-password"}
            hint={isSignup ? "At least 8 characters." : undefined}
            error={fieldErrors.password}
          />
          {error && (
            <p role="alert" className="text-destructive text-sm">
              {error}
            </p>
          )}
        </CardContent>
        <CardFooter className="mt-6">
          <Button type="submit" className="w-full" disabled={submitting}>
            {submitting && <Loader2 className="animate-spin" aria-hidden />}
            {isSignup ? "Sign up" : "Log in"}
          </Button>
        </CardFooter>
      </form>
    </Card>
      <p className="text-muted-foreground mt-5 text-center text-sm">
        {isSignup ? "Already have an account? " : "New to Meridian? "}
        <Link
          href={`${isSignup ? "/login" : "/signup"}${next !== "/trips" ? `?next=${encodeURIComponent(next)}` : ""}`}
          className="text-foreground font-medium underline decoration-contour underline-offset-4 hover:decoration-signal"
        >
          {isSignup ? "Log in" : "Sign up"}
        </Link>
      </p>
    </div>
  );
}

/** Only same-site paths are allowed as redirect targets. */
function safeNext(next: string | null): string {
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/trips";
}

function Field({
  id,
  label,
  type = "text",
  autoComplete,
  hint,
  error,
}: {
  id: string;
  label: string;
  type?: string;
  autoComplete?: string;
  hint?: string;
  error?: string;
}) {
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div className="grid gap-2">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        name={id}
        type={type}
        autoComplete={autoComplete}
        required
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
      />
      {error ? (
        <p id={`${id}-error`} className="text-destructive text-xs">
          {label} {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="text-muted-foreground text-xs">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
