package com.meridian.itinerary;

import com.meridian.ai.AiCacheService;
import com.meridian.ai.AiClient;
import com.meridian.ai.AiException;
import com.meridian.ai.AiRequest;
import com.meridian.ai.AiResult;
import com.meridian.ai.PromptLibrary;
import com.meridian.ai.PromptTemplate;
import com.meridian.place.PlaceCategory;
import com.meridian.profile.Vocabulary;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A4 — plans one day per model call (spec §4 small-model rules). Code has already chosen the day's stops and
 * computed travel times; the model picks the order and start times by short id ({@code s1…}) and writes a note per
 * stop and a sentence for the day. Code validates (every id once, valid times, the day ends in time, no invented
 * numbers), repairs start times that ignore travel, and falls back to {@link RulePlanner} when the model fails.
 */
@Component
public class DayPlanner {

    static final String FEATURE = "itinerary-day";
    static final String PROMPT = "itinerary-day";
    static final int NOTE_MAX = 120;
    static final int SUMMARY_MAX = 200;

    private static final Logger log = LoggerFactory.getLogger(DayPlanner.class);
    private static final Pattern CLOCK = Pattern.compile("\\b([01]\\d|2[0-3]):[0-5]\\d\\b");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern PERIOD = Pattern.compile(
            "\\b(sunrise|breakfast|morning|lunch|afternoon|sunset|evening|dinner|night)s?\\b", Pattern.CASE_INSENSITIVE);
    /** Where a stop's midpoint must fall for text to call it "morning", "sunset" and so on. */
    private static final Map<String, LocalTime[]> PERIODS = Map.of(
            "sunrise", new LocalTime[] {LocalTime.of(5, 0), LocalTime.of(8, 30)},
            "breakfast", new LocalTime[] {LocalTime.of(6, 30), LocalTime.of(11, 0)},
            "morning", new LocalTime[] {LocalTime.of(5, 0), LocalTime.of(12, 30)},
            "lunch", new LocalTime[] {LocalTime.of(11, 30), LocalTime.of(15, 0)},
            "afternoon", new LocalTime[] {LocalTime.of(12, 30), LocalTime.of(17, 30)},
            "sunset", new LocalTime[] {LocalTime.of(16, 30), LocalTime.of(19, 45)},
            "evening", new LocalTime[] {LocalTime.of(16, 30), LocalTime.of(22, 0)},
            "dinner", new LocalTime[] {LocalTime.of(18, 0), LocalTime.of(23, 0)},
            "night", new LocalTime[] {LocalTime.of(19, 0), LocalTime.MAX});
    private static final DateTimeFormatter DAY_NAME = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH);

    /** What the model returns. */
    public record Output(List<Item> stops, String summary) {

        public record Item(String id, String start, String note) {}
    }

    /** Cached per input hash: the model's order, times and words. */
    public record Cached(List<UUID> order, Map<UUID, String> starts, Map<UUID, String> notes, String summary,
            String model, String promptVersion) {}

    /** One planned day. {@code dropped} stops did not fit and go back to the unplaced list. */
    public record PlannedDay(List<Scheduler.Slot> slots, Map<UUID, String> notes, String summary, boolean ai,
            String model, String promptVersion, List<String> repairs, List<Stop> dropped, int overflowMin) {}

    /** Everything the model needs to know about a day besides its stops. */
    public record DayContext(int index, int dayCount, LocalDate date, Pace pace, double fromLat, double fromLng) {}

    private final AiClient ai;
    private final AiCacheService cache;
    private final PromptLibrary prompts;

    public DayPlanner(AiClient ai, AiCacheService cache, PromptLibrary prompts) {
        this.ai = ai;
        this.cache = cache;
        this.prompts = prompts;
    }

    public boolean aiEnabled() {
        return ai.enabled();
    }

    public PlannedDay plan(DayContext day, List<Stop> stops, boolean regenerate) {
        if (stops.isEmpty()) {
            return new PlannedDay(List.of(), Map.of(), "A free day.", false, null, null, List.of(), List.of(), 0);
        }
        RulePlanner.RuleDay rules = RulePlanner.plan(stops, day.pace(), day.fromLat(), day.fromLng());
        List<Stop> kept = rules.order();
        List<String> dropNotes = rules.dropped().stream()
                .map(s -> "Left out " + s.title() + ": the day ran past " + day.pace().dayEnd() + " with it")
                .toList();
        PromptTemplate prompt = prompts.get(PROMPT);
        String key = cache.key(FEATURE, prompt.version(), ai.model(), cacheInput(day, kept));
        if (!regenerate) {
            Optional<Cached> hit = cache.get(key, Cached.class);
            if (hit.isPresent() && hit.get().order().size() == kept.size()) {
                return fromModel(day, kept, hit.get(), rules.dropped(), dropNotes);
            }
        }
        if (!ai.enabled()) return fromRules(day, rules, dropNotes);
        try {
            Map<String, Stop> byShortId = new LinkedHashMap<>();
            IntStream.range(0, kept.size()).forEach(i -> byShortId.put("s" + (i + 1), kept.get(i)));
            String stopLines = byShortId.entrySet().stream().map(e -> stopLine(e.getKey(), e.getValue()))
                    .collect(Collectors.joining("\n"));
            String travel = travelLines(byShortId);
            Map<String, String> vars = Map.of("day", dayLine(day), "pace", paceLine(day.pace()), "stops", stopLines,
                    "travel", travel);
            String facts = String.join("\n", vars.values());
            AiResult<Output> result = ai.generate(new AiRequest<>(FEATURE, prompt, vars, schema(byShortId.keySet()),
                    Output.class, o -> validate(o, byShortId, day.pace(), facts)));
            Output out = result.value();
            List<UUID> order = new ArrayList<>();
            Map<UUID, String> starts = new HashMap<>();
            Map<UUID, String> notes = new HashMap<>();
            for (Output.Item item : out.stops()) {
                Stop stop = byShortId.get(item.id());
                order.add(stop.id());
                starts.put(stop.id(), item.start());
                String note = item.note() == null ? "" : item.note().strip();
                if (!note.isEmpty() && unoffered(note, stopLine(item.id(), stop)).isEmpty()) notes.put(stop.id(), note);
            }
            String summary = out.summary() == null ? "" : out.summary().strip();
            if (summary.isEmpty() || !unoffered(summary, stopLines).isEmpty()) summary = templateSummary(kept);
            Cached value = new Cached(order, starts, notes, summary, result.model(), result.promptVersion());
            cache.put(key, FEATURE, prompt.version(), ai.model(), value);
            return fromModel(day, kept, value, rules.dropped(), dropNotes);
        } catch (AiException e) {
            if (e.reason() == AiException.Reason.CANCELLED) throw e;
            log.info("Itinerary day {} fell back to rules: {}", day.index(), e.getMessage());
            return fromRules(day, rules, dropNotes);
        }
    }

    private PlannedDay fromModel(DayContext day, List<Stop> kept, Cached c, List<Stop> dropped,
            List<String> dropNotes) {
        Map<UUID, Stop> byId = kept.stream().collect(Collectors.toMap(Stop::id, s -> s));
        List<Stop> order = c.order().stream().map(byId::get).toList();
        Map<UUID, LocalTime> requested = new HashMap<>();
        c.starts().forEach((id, t) -> requested.put(id, LocalTime.parse(t)));
        Scheduler.ScheduledDay scheduled = Scheduler.schedule(order, requested, day.pace());
        List<String> repairs = new ArrayList<>(scheduled.repairs());
        repairs.addAll(dropNotes);
        // times are final only now, so text naming a time of day is checked against the scheduled slots
        Map<UUID, String> notes = new HashMap<>();
        for (Scheduler.Slot slot : scheduled.slots()) {
            String note = c.notes().get(slot.stop().id());
            if (note == null) continue;
            if (mistimed(note, List.of(slot)).isEmpty()) notes.put(slot.stop().id(), note);
            else repairs.add("Dropped the note on " + slot.stop().title() + ": it named the wrong time of day for "
                    + slot.start() + ".");
        }
        String summary = c.summary();
        if (!mistimed(summary, scheduled.slots()).isEmpty()) {
            summary = templateSummary(order);
            repairs.add("Replaced the summary: it named a time of day with nothing scheduled then.");
        }
        return new PlannedDay(scheduled.slots(), notes, summary, true, c.model(), c.promptVersion(), repairs,
                dropped, scheduled.overflowMin());
    }

    private static PlannedDay fromRules(DayContext day, RulePlanner.RuleDay rules, List<String> dropNotes) {
        Scheduler.ScheduledDay scheduled = Scheduler.schedule(rules.order(), Map.of(), day.pace());
        return new PlannedDay(scheduled.slots(), Map.of(), templateSummary(rules.order()), false, null, null,
                dropNotes, rules.dropped(), scheduled.overflowMin());
    }

    // ---- prompt input ----------------------------------------------------------------------------------------

    private static Map<String, Object> cacheInput(DayContext day, List<Stop> kept) {
        return Map.of("day", dayLine(day), "pace", day.pace().name(), "stops", kept.stream()
                .sorted(Comparator.comparing(Stop::id))
                .map(s -> Map.of("id", s.id().toString(), "title", s.title(), "minutes", s.durationMin(),
                        "offers", s.offers(), "lat", s.lat(), "lng", s.lng()))
                .toList());
    }

    static String dayLine(DayContext day) {
        return day.date().format(DAY_NAME) + ", day " + day.index() + " of " + day.dayCount();
    }

    static String paceLine(Pace pace) {
        return pace.label() + " (day runs " + pace.dayStart() + " to " + pace.dayEnd() + ")";
    }

    static String stopLine(String id, Stop s) {
        return id + " | " + s.title() + " | " + categoryWord(s.category()) + " | " + s.durationMin() + " min | offers: "
                + s.offers();
    }

    static String travelLines(Map<String, Stop> byShortId) {
        List<String> ids = List.copyOf(byShortId.keySet());
        if (ids.size() < 2) return "none, only one stop";
        List<String> legs = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                legs.add(ids.get(i) + "-" + ids.get(j) + ": "
                        + Stop.leg(byShortId.get(ids.get(i)), byShortId.get(ids.get(j))).minutes() + " min");
            }
        }
        return String.join(" | ", legs);
    }

    private static String categoryWord(PlaceCategory c) {
        return switch (c) {
            case SIGHTS -> "sight";
            case FOOD -> "food";
            case ACTIVITIES -> "activity";
            case STAYS -> "stay";
            default -> "place";
        };
    }

    static String templateSummary(List<Stop> order) {
        if (order.isEmpty()) return "A free day.";
        if (order.size() == 1) return "Just " + order.getFirst().title() + ", at an easy pace.";
        StringBuilder s = new StringBuilder("Starts at ").append(order.getFirst().title());
        order.stream().filter(Stop::isMeal).findFirst()
                .filter(m -> m != order.getFirst() && m != order.getLast())
                .ifPresent(m -> s.append(", with a meal at ").append(m.title()));
        return s.append(" and ends at ").append(order.getLast().title()).append('.').toString();
    }

    // ---- schema and validation ---------------------------------------------------------------------------------

    static Map<String, Object> schema(Set<String> ids) {
        Map<String, Object> item = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("id", "start", "note"),
                "properties", Map.of(
                        "id", Map.of("type", "string", "enum", List.copyOf(ids)),
                        "start", Map.of("type", "string", "pattern", "^([01][0-9]|2[0-3]):[0-5][0-9]$"),
                        "note", Map.of("type", "string", "maxLength", NOTE_MAX)));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("stops", "summary"),
                "properties", Map.of(
                        "stops", Map.of("type", "array", "minItems", ids.size(), "maxItems", ids.size(),
                                "items", item),
                        "summary", Map.of("type", "string", "maxLength", SUMMARY_MAX)));
    }

    /**
     * Every id once, readable times, and the day must end in time once code has applied travel times. Numbers other
     * than clock times must come from the facts.
     */
    static List<String> validate(Output o, Map<String, Stop> byShortId, Pace pace, String facts) {
        if (o == null || o.stops() == null) return List.of("stops is missing");
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Stop> order = new ArrayList<>();
        Map<UUID, LocalTime> requested = new HashMap<>();
        for (Output.Item item : o.stops()) {
            if (item == null || item.id() == null || !byShortId.containsKey(item.id())) {
                errors.add("unknown id \"" + (item == null ? null : item.id()) + "\"");
                continue;
            }
            if (!seen.add(item.id())) {
                errors.add("id \"" + item.id() + "\" appears twice");
                continue;
            }
            Stop stop = byShortId.get(item.id());
            order.add(stop);
            try {
                requested.put(stop.id(), LocalTime.parse(item.start() == null ? "" : item.start()));
            } catch (DateTimeParseException e) {
                errors.add(item.id() + ": start \"" + item.start() + "\" is not HH:MM");
            }
            if (item.note() != null && item.note().length() > NOTE_MAX) {
                errors.add(item.id() + ": note is longer than " + NOTE_MAX + " characters");
            }
            inventedNumbers(item.note(), facts).forEach(n -> errors.add(item.id() + ": the number " + n
                    + " is not in the facts"));
        }
        byShortId.keySet().stream().filter(id -> !seen.contains(id)).forEach(id -> errors.add("missing stop " + id));
        if (o.summary() != null && o.summary().length() > SUMMARY_MAX) {
            errors.add("summary is longer than " + SUMMARY_MAX + " characters");
        }
        inventedNumbers(o.summary(), facts).forEach(n -> errors.add("summary: the number " + n
                + " is not in the facts"));
        if (errors.isEmpty()) {
            Scheduler.ScheduledDay day = Scheduler.schedule(order, requested, pace);
            if (!day.fits()) {
                Scheduler.Slot last = day.slots().getLast();
                errors.add("with travel times the day ends at " + last.end() + ", " + day.overflowMin()
                        + " min after " + pace.dayEnd() + "; start earlier, remove idle gaps or reorder");
            }
        }
        return errors;
    }

    /** Digits in the text that are not part of a clock time and do not appear in the facts. */
    static List<String> inventedNumbers(String text, String facts) {
        if (text == null || text.isBlank()) return List.of();
        Set<String> allowed = new HashSet<>();
        Matcher f = NUMBER.matcher(facts);
        while (f.find()) allowed.add(f.group());
        Matcher m = NUMBER.matcher(CLOCK.matcher(text).replaceAll(" "));
        List<String> out = new ArrayList<>();
        while (m.find()) {
            if (!allowed.contains(m.group())) out.add(m.group());
        }
        return out;
    }

    /**
     * Times of day a sentence names ("sunset", "morning") that none of the slots is centred in, e.g. "evening photos"
     * on a stop scheduled 11:15–12:45.
     */
    static List<String> mistimed(String text, List<Scheduler.Slot> slots) {
        List<String> out = new ArrayList<>();
        Matcher m = PERIOD.matcher(text);
        while (m.find()) {
            String period = m.group(1).toLowerCase(Locale.ROOT);
            LocalTime[] window = PERIODS.get(period);
            boolean fits = slots.stream().anyMatch(s -> {
                LocalTime mid = s.start().plusMinutes(s.stop().durationMin() / 2);
                return !mid.isBefore(window[0]) && mid.isBefore(window[1]);
            });
            if (!fits && !out.contains(period)) out.add(period);
        }
        return out;
    }

    /** Interests a sentence names that the given facts don't mention ("a quiet beach with local food"). */
    static List<String> unoffered(String text, String facts) {
        List<String> offered = Vocabulary.interestsMentioned(facts);
        return Vocabulary.interestsMentioned(text).stream().filter(t -> !offered.contains(t)).toList();
    }
}
