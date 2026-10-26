package com.meridian.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import com.meridian.place.PlaceCategory;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PlanningTest {

    static Stop stop(String title, PlaceCategory category, double lat, double lng, Integer fit) {
        return new Stop(UUID.nameUUIDFromBytes(title.getBytes()), title, category, lat, lng,
                Stop.durationFor(category), fit, "history");
    }

    // North Goa
    static final Stop CHAPORA = stop("Chapora Fort", PlaceCategory.SIGHTS, 15.6060, 73.7366, 80);
    static final Stop ANJUNA = stop("Anjuna Market", PlaceCategory.SIGHTS, 15.5733, 73.7400, 60);
    static final Stop BAGA = stop("Baga Beach", PlaceCategory.SIGHTS, 15.5553, 73.7517, 40);
    // South Goa
    static final Stop COLVA = stop("Colva Beach", PlaceCategory.SIGHTS, 15.2793, 73.9223, 70);
    static final Stop CABO = stop("Cabo de Rama", PlaceCategory.SIGHTS, 15.0886, 73.9196, 90);
    static final Stop PALOLEM = stop("Palolem", PlaceCategory.SIGHTS, 15.0100, 74.0230, 50);

    @Test
    void groupPaceIsTheMedianAndASplitMeetsInTheMiddle() {
        assertThat(Pace.forGroup(List.of("slow", "packed"))).isEqualTo(Pace.BALANCED);
        assertThat(Pace.forGroup(List.of("slow", "slow", "packed"))).isEqualTo(Pace.SLOW);
        assertThat(Pace.forGroup(List.of("packed", "packed"))).isEqualTo(Pace.PACKED);
        assertThat(Pace.forGroup(List.of())).isEqualTo(Pace.BALANCED);
    }

    @Test
    void legsWalkShortHopsAndRideLongerOnes() {
        Stop next = stop("Next door", PlaceCategory.FOOD, 15.6065, 73.7370, null);
        assertThat(Stop.leg(CHAPORA, next).minutes()).isEqualTo(5);
        Stop.Leg ride = Stop.leg(CHAPORA, COLVA);
        assertThat(ride.km()).isBetween(38.0, 45.0);
        assertThat(ride.minutes()).isGreaterThan(100).isEqualTo(ride.minutes() / 5 * 5);
    }

    @Test
    void requestedTimesThatIgnoreTravelAreRepairedAndRecorded() {
        Scheduler.ScheduledDay day = Scheduler.schedule(List.of(CHAPORA, BAGA),
                Map.of(CHAPORA.id(), LocalTime.of(9, 30), BAGA.id(), LocalTime.of(10, 0)), Pace.BALANCED);

        Scheduler.Slot baga = day.slots().get(1);
        assertThat(baga.start()).isAfterOrEqualTo(day.slots().getFirst().end().plusMinutes(baga.travelMin()));
        assertThat(day.repairs()).singleElement().asString().startsWith("Moved Baga Beach from 10:00 to ")
                .contains("min from Chapora Fort");
        assertThat(day.fits()).isTrue();
    }

    @Test
    void reachableRequestedTimesAreKeptAndLongDaysOverflow() {
        Scheduler.ScheduledDay kept = Scheduler.schedule(List.of(CHAPORA, BAGA),
                Map.of(BAGA.id(), LocalTime.of(16, 0)), Pace.BALANCED);
        assertThat(kept.slots().get(1).start()).isEqualTo(LocalTime.of(16, 0));
        assertThat(kept.repairs()).isEmpty();

        List<Stop> fiveActivities = List.of("a", "b", "c", "d", "e").stream()
                .map(n -> stop(n, PlaceCategory.ACTIVITIES, 15.5, 73.8, null)).toList();
        assertThat(Scheduler.schedule(fiveActivities, Map.of(), Pace.SLOW).overflowMin()).isPositive();
    }

    @Test
    void mealsWaitForLunchOrMoveToDinner() {
        Stop seafood = stop("Martin's Corner", PlaceCategory.FOOD, 15.24, 73.93, 60);
        Scheduler.ScheduledDay breakfastTime = Scheduler.schedule(List.of(seafood),
                Map.of(seafood.id(), LocalTime.of(9, 30)), Pace.BALANCED);
        assertThat(breakfastTime.slots().getFirst().start()).isEqualTo(LocalTime.of(12, 0));
        assertThat(breakfastTime.repairs()).containsExactly("Moved Martin's Corner from 09:30 to 12:00, lunch time");

        Scheduler.ScheduledDay teaTime = Scheduler.schedule(List.of(seafood),
                Map.of(seafood.id(), LocalTime.of(16, 0)), Pace.BALANCED);
        assertThat(teaTime.slots().getFirst().start()).isEqualTo(LocalTime.of(19, 0));

        // without a requested time (the rule planner) the meal still waits, but nothing is reported as a repair
        Stop cafe = stop("Cafe by the fort", PlaceCategory.FOOD, 15.6065, 73.7370, null);
        Scheduler.ScheduledDay rules = Scheduler.schedule(List.of(CHAPORA, cafe), Map.of(), Pace.BALANCED);
        assertThat(rules.slots().get(1).start()).isEqualTo(LocalTime.of(12, 0));
        assertThat(rules.repairs()).isEmpty();
    }

    @Test
    void clusteringKeepsNorthAndSouthOnSeparateDays() {
        DayClusterer.Clustering c = DayClusterer.cluster(List.of(CHAPORA, COLVA, ANJUNA, CABO, BAGA, PALOLEM), 2, 4);

        Set<Set<Stop>> days = c.days().stream().map(Set::copyOf).collect(Collectors.toSet());
        assertThat(days).containsExactlyInAnyOrder(Set.of(CHAPORA, ANJUNA, BAGA), Set.of(COLVA, CABO, PALOLEM));
        assertThat(c.dropped()).isEmpty();
        // west to east: the north cluster sits further west
        assertThat(c.days().getFirst()).contains(CHAPORA);
        assertThat(DayClusterer.cluster(List.of(CHAPORA, COLVA, ANJUNA, CABO, BAGA, PALOLEM), 2, 4)).isEqualTo(c);
    }

    @Test
    void tooManyPlacesDropsTheLowestGroupFitAndFewPlacesLeaveFreeDays() {
        DayClusterer.Clustering full = DayClusterer.cluster(List.of(CHAPORA, ANJUNA, BAGA), 1, 2);
        assertThat(full.dropped()).containsExactly(BAGA);

        DayClusterer.Clustering light = DayClusterer.cluster(List.of(CHAPORA, ANJUNA, BAGA), 5, 4);
        assertThat(light.days()).hasSize(5);
        assertThat(light.days().stream().filter(List::isEmpty).count()).isEqualTo(3);
    }

    @Test
    void rulesPutFoodNearLunchAndDropWhatDoesNotFit() {
        Stop lunch = stop("Vinayak", PlaceCategory.FOOD, 15.5800, 73.7450, 70);
        RulePlanner.RuleDay day = RulePlanner.plan(List.of(CHAPORA, ANJUNA, BAGA, lunch), Pace.BALANCED, 15.49, 73.83);

        Scheduler.Slot meal = Scheduler.schedule(day.order(), Map.of(), Pace.BALANCED).slots().stream()
                .filter(s -> s.stop().equals(lunch)).findFirst().orElseThrow();
        assertThat(meal.start()).isBetween(LocalTime.of(11, 45), LocalTime.of(14, 30));
        assertThat(day.dropped()).isEmpty();

        List<Stop> many = List.of("a", "b", "c", "d", "e").stream()
                .map(n -> stop(n, PlaceCategory.ACTIVITIES, 15.5, 73.8, n.equals("c") ? 10 : 60)).toList();
        RulePlanner.RuleDay trimmed = RulePlanner.plan(many, Pace.SLOW, 15.5, 73.8);
        assertThat(trimmed.dropped()).first().extracting(Stop::title).isEqualTo("c");
        assertThat(Scheduler.schedule(trimmed.order(), Map.of(), Pace.SLOW).fits()).isTrue();
    }
}
