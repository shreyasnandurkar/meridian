package com.meridian.itinerary;

import static com.meridian.itinerary.PlanningTest.ANJUNA;
import static com.meridian.itinerary.PlanningTest.BAGA;
import static com.meridian.itinerary.PlanningTest.CHAPORA;
import static com.meridian.itinerary.PlanningTest.stop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.meridian.ai.AiCacheService;
import com.meridian.ai.AiClient;
import com.meridian.ai.AiException;
import com.meridian.ai.AiRequest;
import com.meridian.ai.AiResult;
import com.meridian.ai.PromptLibrary;
import com.meridian.itinerary.DayPlanner.Output;
import com.meridian.itinerary.DayPlanner.Output.Item;
import com.meridian.place.PlaceCategory;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DayPlannerTest {

    /** Answers with a fixed output, validated like the real client (which would retry, then give up). */
    private static AiClient answering(Output output) {
        return new AiClient() {
            public boolean enabled() {
                return true;
            }

            public String model() {
                return "stub";
            }

            @SuppressWarnings("unchecked")
            public <T> AiResult<T> generate(AiRequest<T> request) {
                List<String> errors = request.validator().apply((T) output);
                if (!errors.isEmpty()) throw new AiException(AiException.Reason.INVALID_OUTPUT, errors.toString());
                return new AiResult<>((T) output, "stub", request.prompt().version(), 1, 5);
            }
        };
    }

    private static DayPlanner planner(Output output) {
        return new DayPlanner(answering(output), mock(AiCacheService.class), new PromptLibrary());
    }

    private final DayPlanner.DayContext day = new DayPlanner.DayContext(2, 5, LocalDate.of(2026, 12, 11),
            Pace.BALANCED, 15.49, 73.83);

    /** The rule order for these stops gives the model's ids: s1, s2, s3 in nearest-neighbour order from Panaji. */
    private static Map<String, Stop> shortIds(List<Stop> order) {
        Map<String, Stop> out = new LinkedHashMap<>();
        for (int i = 0; i < order.size(); i++) out.put("s" + (i + 1), order.get(i));
        return out;
    }

    @Test
    void theModelsOrderIsKeptAndTimesThatIgnoreTravelAreRepaired() {
        List<Stop> rules = RulePlanner.plan(List.of(CHAPORA, ANJUNA, BAGA), Pace.BALANCED, 15.49, 73.83).order();
        // reverse order, the second stop booked to start right as the first ends (no travel)
        Output out = new Output(List.of(new Item("s3", "09:30", "Go early."), new Item("s2", "11:00", ""),
                new Item("s1", "15:00", "Bring water.")), "A slow loop of the north.");

        DayPlanner.PlannedDay plan = planner(out).plan(day, List.of(CHAPORA, ANJUNA, BAGA), false);

        assertThat(plan.ai()).isTrue();
        assertThat(plan.slots()).extracting(s -> s.stop().title()).containsExactly(rules.get(2).title(),
                rules.get(1).title(), rules.get(0).title());
        assertThat(plan.slots().get(1).start()).isAfter(LocalTime.of(11, 0));
        assertThat(plan.repairs()).anyMatch(r -> r.startsWith("Moved " + rules.get(1).title() + " from 11:00"));
        assertThat(plan.slots().get(2).start()).isEqualTo(LocalTime.of(15, 0));
        assertThat(plan.summary()).isEqualTo("A slow loop of the north.");
        assertThat(plan.notes()).containsValue("Go early.").hasSize(2);
    }

    @Test
    void invalidOutputFallsBackToTheRules() {
        Output unknownId = new Output(List.of(new Item("s9", "09:30", "")), "x");

        DayPlanner.PlannedDay plan = planner(unknownId).plan(day, List.of(CHAPORA, ANJUNA, BAGA), false);

        assertThat(plan.ai()).isFalse();
        assertThat(plan.slots()).hasSize(3);
        assertThat(plan.summary()).startsWith("Starts at ");
    }

    @Test
    void notesAndSummariesThatClaimWhatThePlaceDoesNotOfferAreDropped() {
        Output padded = new Output(List.of(new Item("s1", "10:00", "Great nightlife here."), new Item("s2", "13:00", "")),
                "Beaches and nightlife all day.");
        Stop a = stop("Fort A", PlaceCategory.SIGHTS, 15.60, 73.73, 50);
        Stop b = stop("Fort B", PlaceCategory.SIGHTS, 15.61, 73.74, 50);

        DayPlanner.PlannedDay plan = planner(padded).plan(day, List.of(a, b), false);

        assertThat(plan.ai()).isTrue();
        assertThat(plan.notes()).isEmpty();
        assertThat(plan.summary()).startsWith("Starts at ");
    }

    @Test
    void textThatNamesTheWrongTimeOfDayIsDroppedOnceTimesAreFinal() {
        // asked for 10:00, but travel pushes the second stop to late morning: "evening" no longer fits
        Stop a = stop("Fort A", PlaceCategory.SIGHTS, 15.60, 73.73, 50);
        Stop b = stop("Fort B", PlaceCategory.SIGHTS, 15.65, 73.78, 50);
        List<Stop> rules = RulePlanner.plan(List.of(a, b), Pace.BALANCED, 15.49, 73.83).order();
        Output out = new Output(List.of(new Item("s1", "09:30", "Clear views in the morning."),
                new Item("s2", "10:00", "Better in the evening.")), "An easy afternoon between Fort A and Fort B.");

        DayPlanner.PlannedDay plan = planner(out).plan(day, List.of(a, b), false);

        assertThat(plan.notes()).containsOnlyKeys(rules.get(0).id());
        assertThat(plan.summary()).startsWith("Starts at ");
        assertThat(plan.repairs()).anyMatch(r -> r.startsWith("Dropped the note on " + rules.get(1).title()))
                .anyMatch(r -> r.startsWith("Replaced the summary"));
        assertThat(DayPlanner.mistimed("Sunset over the fort", plan.slots())).containsExactly("sunset");
    }

    @Test
    void validationRejectsDaysThatEndLateAndInventedNumbers() {
        Map<String, Stop> ids = shortIds(List.of(CHAPORA, BAGA));
        Output late = new Output(List.of(new Item("s1", "18:00", ""), new Item("s2", "19:00", "")), "x");
        assertThat(DayPlanner.validate(late, ids, Pace.BALANCED, "facts"))
                .singleElement().asString().startsWith("with travel times the day ends at ");

        Output numbers = new Output(List.of(new Item("s1", "09:30", "Open since 1612, lunch at 12:30."),
                new Item("s2", "13:00", "")), "Day 2 of the trip.");
        assertThat(DayPlanner.validate(numbers, ids, Pace.BALANCED, "Thursday, day 2 of 5"))
                .containsExactly("s1: the number 1612 is not in the facts");
    }

    @Test
    void emptyDaysAreFreeDays() {
        DayPlanner.PlannedDay plan = planner(new Output(List.of(), "")).plan(day, List.of(), false);
        assertThat(plan.slots()).isEmpty();
        assertThat(plan.summary()).isEqualTo("A free day.");
    }
}
