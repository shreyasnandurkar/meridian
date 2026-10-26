/** Meridian mark: a globe outline crossed by one magenta meridian. */
export function BrandMark({ className = "size-6" }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} aria-hidden fill="none">
      <circle cx="12" cy="12" r="9.25" stroke="currentColor" strokeWidth="1.5" />
      <path d="M2.75 12h18.5M4.4 7.5h15.2M4.4 16.5h15.2" stroke="currentColor" strokeWidth="1" opacity=".45" />
      <path d="M12 2.75c-2.9 2.6-4.3 5.7-4.3 9.25s1.4 6.65 4.3 9.25" stroke="currentColor" strokeWidth="1" opacity=".45" />
      <path d="M12 1.5v21" stroke="var(--signal)" strokeWidth="1.75" strokeLinecap="round" />
    </svg>
  );
}
