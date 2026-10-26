package com.meridian.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ai_call_log")
public class AiCallLog {

    public enum Status { VALID, INVALID, TIMEOUT, ERROR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String feature;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Column(nullable = false)
    private String model;

    @Column(name = "input_hash", nullable = false)
    private String inputHash;

    @Column(nullable = false)
    private int attempt;

    @Column(nullable = false)
    private String status;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiCallLog() {}

    public AiCallLog(String feature, String promptVersion, String model, String inputHash, int attempt, Status status,
            Integer promptTokens, Integer completionTokens, Integer latencyMs, String error) {
        this.feature = feature;
        this.promptVersion = promptVersion;
        this.model = model;
        this.inputHash = inputHash;
        this.attempt = attempt;
        this.status = status.name();
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.latencyMs = latencyMs;
        this.error = error == null ? null : error.substring(0, Math.min(error.length(), 2000));
        this.createdAt = Instant.now();
    }

    public String getStatus() {
        return status;
    }

    public int getAttempt() {
        return attempt;
    }
}
