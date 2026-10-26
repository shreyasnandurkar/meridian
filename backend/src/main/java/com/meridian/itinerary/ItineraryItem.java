package com.meridian.itinerary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

/** One stop on a day: a shortlisted place at a time, with the travel leg from the previous stop. */
@Entity
@Table(name = "itinerary_items")
public class ItineraryItem {

    @Id
    private UUID id;

    @Column(name = "itinerary_id", nullable = false)
    private UUID itineraryId;

    @Column(name = "day_id", nullable = false)
    private UUID dayId;

    @Column(name = "shortlist_item_id", nullable = false)
    private UUID shortlistItemId;

    @Column(nullable = false)
    private int position;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    @Column(name = "travel_min", nullable = false)
    private int travelMin;

    @Column(name = "travel_km")
    private Double travelKm;

    private String note;

    protected ItineraryItem() {}

    public ItineraryItem(UUID itineraryId, UUID dayId, UUID shortlistItemId, int position, Scheduler.Slot slot,
            String note) {
        this.id = UUID.randomUUID();
        this.itineraryId = itineraryId;
        this.dayId = dayId;
        this.shortlistItemId = shortlistItemId;
        this.position = position;
        this.startTime = slot.start();
        this.durationMin = slot.stop().durationMin();
        this.travelMin = slot.travelMin();
        this.travelKm = slot.travelKm();
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public UUID getItineraryId() {
        return itineraryId;
    }

    public UUID getDayId() {
        return dayId;
    }

    public UUID getShortlistItemId() {
        return shortlistItemId;
    }

    public int getPosition() {
        return position;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public int getDurationMin() {
        return durationMin;
    }

    public int getTravelMin() {
        return travelMin;
    }

    public Double getTravelKm() {
        return travelKm;
    }

    public String getNote() {
        return note;
    }
}
