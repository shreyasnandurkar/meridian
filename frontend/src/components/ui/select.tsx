import * as React from "react";

import { cn } from "@/lib/utils";

/** Styled native select — accessible and keyboard-friendly without extra dependencies. */
function NativeSelect({ className, ...props }: React.ComponentProps<"select">) {
  return (
    <select
      data-slot="native-select"
      className={cn(
        "border-input bg-card focus-visible:border-ring focus-visible:ring-ring/25 h-10 w-full rounded-md border px-3 text-sm outline-none focus-visible:ring-[3px]",
        className,
      )}
      {...props}
    />
  );
}

export { NativeSelect };
