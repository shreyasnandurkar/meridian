package com.meridian.ai;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_cache")
public class AiCacheEntry {

    @Id
    @Column(name = "input_hash")
    private String inputHash;

    @Column(nullable = false)
    private String feature;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Column(nullable = false)
    private String model;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode result;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiCacheEntry() {}

    public AiCacheEntry(String inputHash, String feature, String promptVersion, String model, JsonNode result) {
        this.inputHash = inputHash;
        this.feature = feature;
        this.promptVersion = promptVersion;
        this.model = model;
        this.result = result;
        this.createdAt = Instant.now();
    }

    public JsonNode getResult() {
        return result;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }
}
