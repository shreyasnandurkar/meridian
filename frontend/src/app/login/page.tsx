import { Suspense } from "react";

import { AuthForm } from "@/components/auth-form";

export const metadata = { title: "Log in — Meridian" };

export default function LoginPage() {
  return (
    <Suspense>
      <AuthForm mode="login" />
    </Suspense>
  );
}
