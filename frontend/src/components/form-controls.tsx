"use client";

import { useId, useState } from "react";
import { Check, Plus, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Slider } from "@/components/ui/slider";
import type { Option } from "@/lib/types";
import { cn } from "@/lib/utils";

/** Single choice rendered as buttons; a proper radiogroup for keyboard and screen-reader users. */
export function Segmented({
  label,
  options,
  value,
  onChange,
  className,
}: {
  label: string;
  options: Option[];
  value: string;
  onChange: (id: string) => void;
  className?: string;
}) {
  const labelId = useId();
  return (
    <div className={className}>
      <p id={labelId} className="mb-2 text-sm font-medium">
        {label}
      </p>
      <div role="radiogroup" aria-labelledby={labelId} className="flex flex-wrap gap-2">
        {options.map((o, i) => {
          const checked = o.id === value;
          return (
            <button
              key={o.id}
              type="button"
              role="radio"
              aria-checked={checked}
              tabIndex={checked || (!value && i === 0) ? 0 : -1}
              onClick={() => onChange(o.id)}
              onKeyDown={(e) => {
                if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
                e.preventDefault();
                const next = options[(i + (e.key === "ArrowRight" ? 1 : options.length - 1)) % options.length];
                onChange(next.id);
                (e.currentTarget.parentElement?.children[options.indexOf(next)] as HTMLElement | undefined)?.focus();
              }}
              className={cn(
                "focus-visible:ring-ring/50 rounded-md border px-3 py-2 text-left text-sm transition-colors outline-none focus-visible:ring-[3px]",
                checked
                  ? "border-foreground/60 bg-sounding/50 text-foreground"
                  : "hover:bg-muted text-muted-foreground border-border",
              )}
            >
              {checked && <Check className="text-foreground mr-1.5 inline size-3.5 align-[-2px]" aria-hidden />}
              {o.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

/** Multi-select toggle chips. */
export function ChipGroup({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: Option[];
  value: string[];
  onChange: (ids: string[]) => void;
}) {
  const labelId = useId();
  return (
    <div>
      <p id={labelId} className="mb-2 text-sm font-medium">
        {label}
      </p>
      <div role="group" aria-labelledby={labelId} className="flex flex-wrap gap-2">
        {options.map((o) => {
          const on = value.includes(o.id);
          return (
            <button
              key={o.id}
              type="button"
              aria-pressed={on}
              onClick={() => onChange(on ? value.filter((v) => v !== o.id) : [...value, o.id])}
              className={cn(
                "focus-visible:ring-ring/50 inline-flex items-center gap-1 rounded-full border px-3 py-1 text-sm transition-colors outline-none focus-visible:ring-[3px]",
                on ? "border-foreground/60 bg-sounding/50 text-foreground" : "text-muted-foreground hover:bg-muted",
              )}
            >
              {on && <Check className="text-foreground size-3.5" aria-hidden />}
              {o.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

/** Free-text list ("must-haves", "dealbreakers"). Enter adds an item. */
export function TagInput({
  label,
  hint,
  value,
  onChange,
  max = 8,
  maxLength = 80,
  placeholder,
}: {
  label: string;
  hint?: string;
  value: string[];
  onChange: (items: string[]) => void;
  max?: number;
  maxLength?: number;
  placeholder?: string;
}) {
  const id = useId();
  const [draft, setDraft] = useState("");
  const add = () => {
    const item = draft.trim();
    if (!item || value.length >= max || value.some((v) => v.toLowerCase() === item.toLowerCase())) return;
    onChange([...value, item]);
    setDraft("");
  };
  return (
    <div>
      <label htmlFor={id} className="mb-2 block text-sm font-medium">
        {label}
      </label>
      {value.length > 0 && (
        <ul className="mb-2 flex flex-wrap gap-2" aria-label={`${label} added`}>
          {value.map((item) => (
            <li key={item} className="bg-secondary inline-flex items-center gap-1 rounded-full py-1 pr-1 pl-3 text-sm">
              {item}
              <button
                type="button"
                onClick={() => onChange(value.filter((v) => v !== item))}
                className="hover:bg-background focus-visible:ring-ring/50 rounded-full p-0.5 outline-none focus-visible:ring-2"
                aria-label={`Remove ${item}`}
              >
                <X className="size-3.5" aria-hidden />
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="flex gap-2">
        <Input
          id={id}
          value={draft}
          maxLength={maxLength}
          placeholder={placeholder}
          disabled={value.length >= max}
          aria-describedby={hint ? `${id}-hint` : undefined}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              add();
            }
          }}
        />
        <Button type="button" variant="outline" onClick={add} disabled={!draft.trim() || value.length >= max}>
          <Plus aria-hidden /> Add
        </Button>
      </div>
      {hint && (
        <p id={`${id}-hint`} className="text-muted-foreground mt-1.5 text-xs">
          {hint}
        </p>
      )}
    </div>
  );
}

/** A slider with a visible label, value text and end labels. */
export function LabeledSlider({
  label,
  value,
  onChange,
  min = 0,
  max = 100,
  step = 1,
  valueText,
  left,
  right,
}: {
  label: string;
  value: number;
  onChange: (v: number) => void;
  min?: number;
  max?: number;
  step?: number;
  valueText: string;
  left?: string;
  right?: string;
}) {
  return (
    <div>
      <div className="mb-2 flex items-baseline justify-between gap-4">
        <span className="text-sm font-medium">{label}</span>
        <span className="text-muted-foreground text-sm">{valueText}</span>
      </div>
      <Slider
        aria-label={label}
        aria-valuetext={valueText}
        min={min}
        max={max}
        step={step}
        value={[value]}
        onValueChange={([v]) => onChange(v)}
      />
      {(left || right) && (
        <div className="text-muted-foreground mt-1.5 flex justify-between text-xs">
          <span>{left}</span>
          <span>{right}</span>
        </div>
      )}
    </div>
  );
}
