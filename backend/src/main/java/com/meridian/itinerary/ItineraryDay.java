package com.meridian.itinerary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One day of an itinerary, with how it was planned (model or rules) and what code repaired afterwards. */
@Entity
@Table(name = "itinerary_days")
public class ItineraryDay {

    public enum Source { AI, RULES }

    @Id
    private UUID id;

    @Column(name = "itinerary_id", nullable = false)
    private UUID itineraryId;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    @Column(nullable = false)
    private LocalDate date;

    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    private String model;

    @Column(name = "prompt_version")
    private String promptVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> repairs = List.of();

    protected ItineraryDay() {}

    public ItineraryDay(UUID itineraryId, int dayIndex, LocalDate date) {
        this.id = UUID.randomUUID();
        this.itineraryId = itineraryId;
        this.dayIndex = dayIndex;
        this.date = date;
        this.source = Source.RULES;
    }

    public void planned(String summary, boolean ai, String model, String promptVersion, List<String> repairs) {
        this.summary = summary;
        this.source = ai ? Source.AI : Source.RULES;
        this.model = model;
        this.promptVersion = promptVersion;
        this.repairs = List.copyOf(repairs);
    }

    /**
     * Someone changed the stops by hand; the times were recomputed by code. The old summary may name a stop that is
     * gone, so it is replaced by one written from the new stops.
     */
    public void editedByHand(String note, String newSummary) {
        List<String> next = new ArrayList<>(repairs);
        if (!next.contains(note)) next.add(note);
        if (!Objects.equals(summary, newSummary) && !next.contains(SUMMARY_REWRITTEN)) next.add(SUMMARY_REWRITTEN);
        this.repairs = List.copyOf(next);
        this.summary = newSummary;
    }

    static final String SUMMARY_REWRITTEN = "Rewrote the summary from the new stops after the change.";

    public UUID getId() {
        return id;
    }

    public UUID getItineraryId() {
        return itineraryId;
    }

    public int getDayIndex() {
        return dayIndex;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getSummary() {
        return summary;
    }

    public Source getSource() {
        return source;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public List<String> getRepairs() {
        return repairs;
    }
}
