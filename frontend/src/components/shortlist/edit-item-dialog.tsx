"use client";

import { useId, useState } from "react";
import { Loader2, Trash2 } from "lucide-react";

import { CategoryField, ReasonField } from "@/components/shortlist/add-place-dialog";
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
import { useRemoveShortlistItem, useUpdateShortlistItem } from "@/lib/queries";
import type { PlaceCategory, ShortlistItem } from "@/lib/types";

/** Edit or remove an item. Only rendered for the person who added it and the organizer. */
export function EditItemDialog({
  tripId,
  item,
  onOpenChange,
}: {
  tripId: string;
  item: ShortlistItem | null;
  onOpenChange: (open: boolean) => void;
}) {
  return (
    <Dialog open={!!item} onOpenChange={onOpenChange}>
      <DialogContent>
        {item && <EditForm key={item.id} tripId={tripId} item={item} close={() => onOpenChange(false)} />}
      </DialogContent>
    </Dialog>
  );
}

function EditForm({ tripId, item, close }: { tripId: string; item: ShortlistItem; close: () => void }) {
  const update = useUpdateShortlistItem(tripId);
  const remove = useRemoveShortlistItem(tripId);
  const [title, setTitle] = useState(item.title);
  const [category, setCategory] = useState<PlaceCategory>(item.category);
  const [reason, setReason] = useState(item.reason ?? "");
  const [notes, setNotes] = useState(item.notes ?? "");
  const [confirming, setConfirming] = useState(false);
  const titleId = useId();
  const notesId = useId();

  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault();
        update.mutate(
          {
            id: item.id,
            input: { category, reason, notes, ...(item.kind !== "PLACE" ? { title: title.trim() } : {}) },
          },
          { onSuccess: close },
        );
      }}
    >
      <DialogHeader>
        <DialogTitle>
          Edit <span className="place">{item.title}</span>
        </DialogTitle>
        <DialogDescription>Changes are visible to the whole group.</DialogDescription>
      </DialogHeader>
      {item.kind !== "PLACE" && (
        <div>
          <label htmlFor={titleId} className="mb-2 block text-sm font-medium">
            {item.kind === "LINK" ? "Title" : "Name"}
          </label>
          <Input id={titleId} required value={title} maxLength={300} onChange={(e) => setTitle(e.target.value)} />
        </div>
      )}
      <CategoryField value={category} onChange={setCategory} />
      <ReasonField value={reason} onChange={setReason} />
      <div>
        <label htmlFor={notesId} className="mb-2 block text-sm font-medium">
          Notes <span className="text-muted-foreground font-normal">(opening hours, how to get there…)</span>
        </label>
        <Textarea id={notesId} value={notes} maxLength={2000} className="min-h-20" onChange={(e) => setNotes(e.target.value)} />
      </div>
      {(update.isError || remove.isError) && (
        <p role="alert" className="text-destructive text-sm">
          {(update.error ?? remove.error)?.message}
        </p>
      )}
      <DialogFooter className="sm:justify-between">
        {confirming ? (
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="destructive"
              onClick={() => remove.mutate(item.id, { onSuccess: close })}
              disabled={remove.isPending}
            >
              <Trash2 aria-hidden /> Yes, remove it
            </Button>
            <Button type="button" variant="ghost" onClick={() => setConfirming(false)}>
              Keep
            </Button>
          </div>
        ) : (
          <Button type="button" variant="ghost" className="text-destructive" onClick={() => setConfirming(true)}>
            <Trash2 aria-hidden /> Remove from shortlist
          </Button>
        )}
        <Button type="submit" disabled={update.isPending || (item.kind !== "PLACE" && !title.trim())}>
          {update.isPending && <Loader2 className="animate-spin" aria-hidden />} Save
        </Button>
      </DialogFooter>
    </form>
  );
}
