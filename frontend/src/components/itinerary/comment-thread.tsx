"use client";

import { useState } from "react";
import { Send, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { formatRelative } from "@/lib/format";
import { useAddComment, useComments, useDeleteComment } from "@/lib/queries";
import type { CommentTarget } from "@/lib/types";

/** Flat comments on one shortlisted place or one proposal (spec §5.7). Comments on a place survive regeneration. */
export function CommentThread({
  tripId,
  targetType,
  targetId,
  label,
}: {
  tripId: string;
  targetType: CommentTarget;
  targetId: string;
  /** What the thread is about, for the field label, e.g. "Colva Beach". */
  label: string;
}) {
  const comments = useComments(tripId);
  const add = useAddComment(tripId);
  const remove = useDeleteComment(tripId);
  const [body, setBody] = useState("");
  const thread = (comments.data ?? []).filter((c) => c.targetType === targetType && c.targetId === targetId);
  const fieldId = `comment-${targetId}`;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const text = body.trim();
    if (!text) return;
    add.mutate({ targetType, targetId, body: text }, { onSuccess: () => setBody("") });
  };

  return (
    <div className="bg-muted/40 mt-3 space-y-3 rounded-lg border p-3">
      {comments.isPending ? (
        <p className="text-muted-foreground text-xs">Loading comments…</p>
      ) : thread.length === 0 ? (
        <p className="text-muted-foreground text-xs">No comments yet.</p>
      ) : (
        <ul className="space-y-2">
          {thread.map((c) => (
            <li key={c.id} className="text-sm">
              <div className="flex items-baseline gap-2">
                <span className="font-medium">{c.author.displayName}</span>
                <span className="text-muted-foreground text-xs">{formatRelative(c.createdAt)}</span>
                {c.canDelete && (
                  <Button
                    variant="ghost"
                    size="sm"
                    className="ml-auto h-6 px-1.5"
                    onClick={() => remove.mutate(c.id)}
                    disabled={remove.isPending}
                    aria-label={`Delete comment by ${c.author.displayName}`}
                  >
                    <Trash2 className="size-3.5" aria-hidden />
                  </Button>
                )}
              </div>
              <p className="mt-0.5 whitespace-pre-wrap">{c.body}</p>
            </li>
          ))}
        </ul>
      )}
      <form onSubmit={submit} className="flex items-end gap-2">
        <label htmlFor={fieldId} className="sr-only">
          Comment on {label}
        </label>
        <Textarea
          id={fieldId}
          value={body}
          onChange={(e) => setBody(e.target.value)}
          maxLength={1000}
          rows={1}
          placeholder="Add a comment"
          // the thread opens on request, so the field is where the user means to type
          autoFocus
          className="min-h-9 py-1.5"
        />
        <Button type="submit" size="sm" disabled={!body.trim() || add.isPending} aria-label="Post comment">
          <Send aria-hidden />
        </Button>
      </form>
      {add.error && (
        <p role="alert" className="text-destructive text-xs">
          {add.error.message}
        </p>
      )}
    </div>
  );
}
