"use client";

import { Map } from "lucide-react";

import { RequireAuth } from "@/components/require-auth";
import { useAuth } from "@/lib/auth";

export default function TripsPage() {
  return (
    <RequireAuth>
      <TripsContent />
    </RequireAuth>
  );
}

function TripsContent() {
  const { user } = useAuth();
  return (
    <div className="py-12">
      <h1 className="text-2xl font-semibold tracking-tight">My trips</h1>
      <p className="text-muted-foreground mt-1 text-sm">Signed in as {user?.email}</p>

      <div className="mt-8 flex flex-col items-center rounded-xl border border-dashed px-6 py-16 text-center">
        <Map className="text-muted-foreground size-8" aria-hidden />
        <h2 className="mt-4 font-medium">No trips yet</h2>
        <p className="text-muted-foreground mt-1 max-w-sm text-sm">
          Creating trips and inviting your group arrives in the next milestone.
        </p>
      </div>
    </div>
  );
}
