package com.meridian.ai.job;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** In-memory state of one async AI job. Fields are written by the worker thread and read by pollers. */
public class AiJob {

    public enum Status { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

    private final UUID id = UUID.randomUUID();
    private final UUID ownerId;
    private final String feature;
    private final Instant createdAt = Instant.now();

    private volatile Status status = Status.QUEUED;
    private volatile int percent;
    private volatile String message = "Queued";
    private volatile Object result;
    private volatile String error;
    private volatile Instant finishedAt;
    private volatile Future<?> future;
    /** Told about every change of status or progress; used to publish trip events. */
    private volatile Consumer<AiJob> listener = j -> { };

    AiJob(UUID ownerId, String feature) {
        this.ownerId = ownerId;
        this.feature = feature;
    }

    void progress(int percent, String message) {
        if (status.ordinal() >= Status.SUCCEEDED.ordinal()) return;
        this.status = Status.RUNNING;
        this.percent = Math.max(this.percent, Math.min(100, percent));
        this.message = message;
        listener.accept(this);
    }

    void succeed(Object result) {
        if (isTerminal()) return;
        this.result = result;
        this.percent = 100;
        this.message = "Done";
        this.status = Status.SUCCEEDED;
        this.finishedAt = Instant.now();
        listener.accept(this);
    }

    void fail(String error) {
        if (isTerminal()) return;
        this.error = error;
        this.message = "Failed";
        this.status = Status.FAILED;
        this.finishedAt = Instant.now();
        listener.accept(this);
    }

    void cancelled() {
        if (isTerminal()) return;
        this.message = "Cancelled";
        this.status = Status.CANCELLED;
        this.finishedAt = Instant.now();
        listener.accept(this);
    }

    boolean isTerminal() {
        return status == Status.SUCCEEDED || status == Status.FAILED || status == Status.CANCELLED;
    }

    void listen(Consumer<AiJob> listener) {
        this.listener = listener;
    }

    void attach(Future<?> future) {
        this.future = future;
    }

    Future<?> future() {
        return future;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getFeature() {
        return feature;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Status getStatus() {
        return status;
    }

    public int getPercent() {
        return percent;
    }

    public String getMessage() {
        return message;
    }

    public Object getResult() {
        return result;
    }

    public String getError() {
        return error;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
