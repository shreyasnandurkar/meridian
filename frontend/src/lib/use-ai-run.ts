"use client";

import { useEffect, useRef, useState } from "react";

import { useAiJob, useCancelJob } from "@/lib/queries";
import type { AiJob } from "@/lib/types";

/**
 * Follows one async AI job at a time: call `track(job)` with the job a mutation started; `onDone` gets the result
 * once it succeeds. The UI keeps working while the job runs (spec §4: never block on a model call).
 */
export function useAiRun<T>(onDone: (result: T) => void) {
  const [jobId, setJobId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const job = useAiJob<T>(jobId);
  const cancel = useCancelJob();
  const done = useRef(onDone);
  done.current = onDone;

  const status = job.data?.status;
  useEffect(() => {
    if (!jobId || !status || status === "QUEUED" || status === "RUNNING") return;
    if (status === "SUCCEEDED" && job.data?.result) done.current(job.data.result);
    if (status === "FAILED") setError(job.data?.error ?? "The AI job failed.");
    setJobId(null);
  }, [jobId, status, job.data]);

  return {
    running: jobId !== null,
    job: jobId ? job.data : undefined,
    error,
    track: (started: AiJob<T>) => {
      setError(null);
      setJobId(started.id);
    },
    cancel: () => {
      if (jobId) cancel.mutate(jobId);
    },
    cancelling: cancel.isPending,
  };
}
