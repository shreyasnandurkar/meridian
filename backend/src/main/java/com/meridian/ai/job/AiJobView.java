package com.meridian.ai.job;

import java.time.Instant;
import java.util.UUID;

public record AiJobView(
        UUID id,
        String feature,
        AiJob.Status status,
        int percent,
        String message,
        Object result,
        String error,
        Instant createdAt,
        Instant finishedAt) {

    public static AiJobView from(AiJob job) {
        return new AiJobView(job.getId(), job.getFeature(), job.getStatus(), job.getPercent(), job.getMessage(),
                job.getResult(), job.getError(), job.getCreatedAt(), job.getFinishedAt());
    }
}
