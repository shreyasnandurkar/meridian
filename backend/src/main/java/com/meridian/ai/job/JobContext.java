package com.meridian.ai.job;

/** Handed to job work so it can report progress. */
public interface JobContext {

    void progress(int percent, String message);
}
