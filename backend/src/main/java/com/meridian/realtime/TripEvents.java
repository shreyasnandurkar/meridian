package com.meridian.realtime;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes what changed in a trip to {@code /topic/trips/{id}} (spec §7). Events say what changed, not the new
 * state: views are per viewer, so clients refetch what they show. Inside a transaction an event is sent only after
 * the commit, so a client never refetches data that is not there yet, and a rolled-back change sends nothing.
 */
@Component
public class TripEvents {

    /** What a client should refresh. */
    public enum Type {
        PROPOSALS, VOTES, APPROVAL, ITINERARY, DECISIONS, COMMENTS, SHORTLIST, MEMBERS, TRIP, AI_JOB
    }

    public record Event(Type type, UUID tripId, UUID actorId, Map<String, Object> data, Instant at) {}

    private static final Logger log = LoggerFactory.getLogger(TripEvents.class);

    private final SimpMessagingTemplate broker;

    public TripEvents(SimpMessagingTemplate broker) {
        this.broker = broker;
    }

    public static String topic(UUID tripId) {
        return "/topic/trips/" + tripId;
    }

    public void publish(UUID tripId, Type type, UUID actorId) {
        publish(tripId, type, actorId, Map.of());
    }

    public void publish(UUID tripId, Type type, UUID actorId, Map<String, Object> data) {
        Event event = new Event(type, tripId, actorId, data, Instant.now());
        afterCommit(() -> send(event));
    }

    /** Runs {@code action} after the current transaction commits, or now when there is none. */
    public static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void send(Event event) {
        try {
            broker.convertAndSend(topic(event.tripId()), event);
        } catch (RuntimeException e) {
            // live updates are a convenience: clients still refetch on focus and after their own changes
            log.warn("Could not publish {} for trip {}: {}", event.type(), event.tripId(), e.getMessage());
        }
    }
}
