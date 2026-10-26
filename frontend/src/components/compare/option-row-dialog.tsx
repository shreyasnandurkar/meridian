"use client";

import { useId, useState } from "react";
import { Link2, Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useOptionDraft, useSaveOptionRow } from "@/lib/queries";
import type { Comparison, OptionRow } from "@/lib/types";

/** Add or edit one option. Paste a provider link to prefill it; prices are always entered or confirmed by a person. */
export function OptionRowDialog({
  tripId,
  currency,
  comparison,
  row,
  open,
  onOpenChange,
}: {
  tripId: string;
  currency: string;
  comparison: Comparison | null;
  row: OptionRow | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  return (
    <Dialog open={open && !!comparison} onOpenChange={onOpenChange}>
      <DialogContent>
        {comparison && (
          <RowForm
            key={row?.id ?? "new"}
            tripId={tripId}
            currency={currency}
            comparison={comparison}
            row={row}
            close={() => onOpenChange(false)}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function RowForm({
  tripId,
  currency,
  comparison,
  row,
  close,
}: {
  tripId: string;
  currency: string;
  comparison: Comparison;
  row: OptionRow | null;
  close: () => void;
}) {
  const save = useSaveOptionRow(tripId);
  const draft = useOptionDraft(tripId);
  const [link, setLink] = useState(row?.sourceUrl ?? "");
  const [provider, setProvider] = useState(row?.provider ?? "");
  const [title, setTitle] = useState(row?.title ?? "");
  const [price, setPrice] = useState(row?.price != null ? String(row.price) : "");
  const [cur, setCur] = useState(row?.currency ?? currency);
  const [notes, setNotes] = useState(row?.notes ?? "");
  const [sponsored, setSponsored] = useState(row?.sponsored ?? false);
  const [adapter, setAdapter] = useState<string | null>(row?.adapter ?? null);
  const ids = { link: useId(), provider: useId(), title: useId(), price: useId(), cur: useId(), notes: useId() };

  const readLink = () =>
    draft.mutate(link.trim(), {
      onSuccess: (d) => {
        setProvider(d.provider);
        if (d.title) setTitle(d.title);
        if (d.price != null) setPrice(String(d.price));
        if (d.currency) setCur(d.currency);
        setLink(d.sourceUrl);
        setAdapter(d.adapter);
      },
    });

  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault();
        const amount = price.trim() === "" ? null : Number(price);
        save.mutate(
          {
            comparisonId: comparison.id,
            rowId: row?.id,
            input: {
              provider: provider.trim(),
              title: title.trim() || null,
              price: amount,
              currency: amount == null ? null : cur.trim().toUpperCase(),
              sourceUrl: link.trim() || null,
              sponsored,
              notes: notes.trim() || null,
              adapter: adapter ?? "manual",
            },
          },
          { onSuccess: close },
        );
      }}
    >
      <DialogHeader>
        <DialogTitle>{row ? "Edit option" : "Add an option"}</DialogTitle>
        <DialogDescription>
          For <span className="font-medium">{comparison.title}</span>. Saving a price stamps it with today&apos;s
          date so everyone can see how fresh it is. Meridian never books; you&apos;ll book on the provider&apos;s site.
        </DialogDescription>
      </DialogHeader>

      <div>
        <label htmlFor={ids.link} className="mb-2 block text-sm font-medium">
          Link <span className="text-muted-foreground font-normal">(optional)</span>
        </label>
        <div className="flex gap-2">
          <Input
            id={ids.link}
            type="url"
            inputMode="url"
            placeholder="https://www.booking.com/…"
            value={link}
            maxLength={2048}
            onChange={(e) => setLink(e.target.value)}
          />
          <Button type="button" variant="outline" onClick={readLink} disabled={!link.trim() || draft.isPending}>
            {draft.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <Link2 aria-hidden />}
            Read link
          </Button>
        </div>
        {draft.error && (
          <p role="alert" className="text-destructive mt-1.5 text-xs">
            {draft.error.message}
          </p>
        )}
        {draft.isSuccess && (
          <p className="text-muted-foreground mt-1.5 text-xs" role="status">
            Filled in from the page{draft.data.price == null ? "; it didn't show a price, so add it below" : ""}.
          </p>
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor={ids.provider} className="mb-2 block text-sm font-medium">
            Provider
          </label>
          <Input id={ids.provider} required maxLength={80} value={provider} onChange={(e) => setProvider(e.target.value)} />
        </div>
        <div>
          <label htmlFor={ids.title} className="mb-2 block text-sm font-medium">
            Option
          </label>
          <Input
            id={ids.title}
            maxLength={300}
            placeholder="Room, flight or tour name"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </div>
        <div>
          <label htmlFor={ids.price} className="mb-2 block text-sm font-medium">
            Price per person
          </label>
          <Input
            id={ids.price}
            type="number"
            min={0}
            step="0.01"
            inputMode="decimal"
            value={price}
            onChange={(e) => setPrice(e.target.value)}
          />
        </div>
        <div>
          <label htmlFor={ids.cur} className="mb-2 block text-sm font-medium">
            Currency
          </label>
          <Input
            id={ids.cur}
            maxLength={3}
            pattern="[A-Za-z]{3}"
            required={price.trim() !== ""}
            value={cur}
            onChange={(e) => setCur(e.target.value.toUpperCase())}
          />
        </div>
      </div>

      <div>
        <label htmlFor={ids.notes} className="mb-2 block text-sm font-medium">
          Notes
        </label>
        <Textarea id={ids.notes} rows={2} maxLength={1000} value={notes} onChange={(e) => setNotes(e.target.value)} />
      </div>

      <label className="flex items-start gap-2 text-sm">
        <input
          type="checkbox"
          className="accent-primary mt-0.5 size-4"
          checked={sponsored}
          onChange={(e) => setSponsored(e.target.checked)}
        />
        <span>
          This listing was sponsored or promoted on the provider&apos;s site
          <span className="text-muted-foreground block text-xs">
            It gets a visible label. Sponsorship never changes the order.
          </span>
        </span>
      </label>

      {save.error && (
        <p role="alert" className="text-destructive text-sm">
          {save.error.message}
        </p>
      )}
      <DialogFooter>
        <Button type="button" variant="ghost" onClick={close}>
          Cancel
        </Button>
        <Button type="submit" disabled={save.isPending || !provider.trim()}>
          {save.isPending && <Loader2 className="animate-spin" aria-hidden />}
          {row ? "Save" : "Add option"}
        </Button>
      </DialogFooter>
    </form>
  );
}
