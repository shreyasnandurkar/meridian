"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";

import { DestinationInput, type Destination } from "@/components/destination-input";
import { RequireAuth } from "@/components/require-auth";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/select";
import { ApiError } from "@/lib/api";
import { useCreateTrip } from "@/lib/queries";

const CURRENCIES = ["INR", "USD", "EUR", "GBP", "AED", "SGD", "THB", "JPY", "AUD"];

export default function NewTripPage() {
  return (
    <RequireAuth>
      <NewTripForm />
    </RequireAuth>
  );
}

function NewTripForm() {
  const router = useRouter();
  const create = useCreateTrip();
  const [name, setName] = useState("");
  const [destination, setDestination] = useState<Destination>({ name: "", lat: null, lng: null });
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [currency, setCurrency] = useState("INR");
  const [budget, setBudget] = useState("");
  const [localError, setLocalError] = useState<string | null>(null);

  const fieldErrors = create.error instanceof ApiError ? (create.error.problem.errors ?? {}) : {};

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLocalError(null);
    if (!name.trim() || !destination.name.trim() || !startDate || !endDate) {
      setLocalError("Please fill in the name, destination and dates.");
      return;
    }
    if (endDate < startDate) {
      setLocalError("The trip must end on or after its start date.");
      return;
    }
    create.mutate(
      {
        name: name.trim(),
        destinationName: destination.name.trim(),
        destinationLat: destination.lat,
        destinationLng: destination.lng,
        startDate,
        endDate,
        currency,
        budgetTotal: budget ? Number(budget) : null,
      },
      { onSuccess: (trip) => router.push(`/trips/${trip.id}?tab=members`) },
    );
  }

  const error = localError ?? (create.isError ? create.error.message : null);
  return (
    <Card className="mx-auto my-10 max-w-xl">
      <CardHeader>
        <CardTitle className="text-xl">New trip</CardTitle>
        <CardDescription>You&apos;ll be the organizer. Invite the group on the next screen.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="grid gap-5" noValidate>
          <div className="grid gap-2">
            <Label htmlFor="trip-name">Trip name</Label>
            <Input
              id="trip-name"
              maxLength={120}
              placeholder="Goa with the college gang"
              value={name}
              aria-invalid={!!fieldErrors.name || undefined}
              onChange={(e) => setName(e.target.value)}
            />
          </div>
          <DestinationInput value={destination} onChange={setDestination} invalid={!!fieldErrors.destinationName} />
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="grid gap-2">
              <Label htmlFor="start">Start date</Label>
              <Input id="start" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="end">End date</Label>
              <Input id="end" type="date" min={startDate || undefined} value={endDate} onChange={(e) => setEndDate(e.target.value)} />
            </div>
          </div>
          <div className="grid gap-4 sm:grid-cols-[8rem_1fr]">
            <div className="grid gap-2">
              <Label htmlFor="currency">Currency</Label>
              <NativeSelect id="currency" value={currency} onChange={(e) => setCurrency(e.target.value)}>
                {CURRENCIES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </NativeSelect>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="budget">Rough total budget per person (optional)</Label>
              <Input
                id="budget"
                type="number"
                inputMode="numeric"
                min={0}
                step={500}
                value={budget}
                onChange={(e) => setBudget(e.target.value)}
              />
            </div>
          </div>
          {error && (
            <p role="alert" className="text-destructive text-sm">
              {error}
            </p>
          )}
          <Button type="submit" disabled={create.isPending}>
            {create.isPending && <Loader2 className="animate-spin" aria-hidden />}
            Create trip
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
