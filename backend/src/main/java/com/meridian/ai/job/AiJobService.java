package com.meridian.ai.job;

import com.meridian.ai.AiException;
import com.meridian.common.ApiException;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs AI work off the request thread so the UI never blocks on a model call. Jobs live in memory for an hour;
 * model concurrency itself is limited inside the AiClient ({@code AI_MAX_CONCURRENCY}).
 */
@Service
public class AiJobService {

    private static final Logger log = LoggerFactory.getLogger(AiJobService.class);
    private static final Duration RETENTION = Duration.ofHours(1);

    private final Map<UUID, AiJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AiJob submit(UUID ownerId, String feature, Function<JobContext, Object> work) {
        evictOld();
        AiJob job = new AiJob(ownerId, feature);
        jobs.put(job.getId(), job);
        job.attach(executor.submit(() -> run(job, work)));
        return job;
    }

    private void run(AiJob job, Function<JobContext, Object> work) {
        try {
            job.progress(1, "Starting");
            Object result = work.apply(job::progress);
            if (Thread.currentThread().isInterrupted() || job.getStatus() == AiJob.Status.CANCELLED) {
                job.cancelled();
            } else {
                job.succeed(result);
            }
        } catch (AiException e) {
            if (e.reason() == AiException.Reason.CANCELLED) job.cancelled();
            else job.fail(e.userMessage());
        } catch (ApiException e) {
            job.fail(e.getMessage());
        } catch (RuntimeException e) {
            log.error("AI job {} ({}) failed", job.getId(), job.getFeature(), e);
            job.fail("Something went wrong while generating this result.");
        }
    }

    /** Returns the job if it belongs to {@code userId}; other users' jobs look like missing ones. */
    public AiJob get(UUID jobId, UUID userId) {
        AiJob job = jobs.get(jobId);
        if (job == null || !job.getOwnerId().equals(userId)) {
            throw ApiException.notFound("AI job not found");
        }
        return job;
    }

    public AiJob cancel(UUID jobId, UUID userId) {
        AiJob job = get(jobId, userId);
        if (!job.isTerminal()) {
            job.cancelled();
            if (job.future() != null) job.future().cancel(true);
        }
        return job;
    }

    private void evictOld() {
        Instant cutoff = Instant.now().minus(RETENTION);
        jobs.values().removeIf(j -> j.isTerminal() && j.getFinishedAt() != null && j.getFinishedAt().isBefore(cutoff));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
