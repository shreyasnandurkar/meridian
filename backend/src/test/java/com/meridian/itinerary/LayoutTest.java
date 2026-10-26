package com.meridian.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LayoutTest {

    private final UUID fort = UUID.randomUUID();
    private final UUID beach = UUID.randomUUID();
    private final UUID cafe = UUID.randomUUID();
    private final UUID market = UUID.randomUUID();
    private final UUID spare = UUID.randomUUID();
    private final Map<UUID, String> titles = Map.of(fort, "Fort", beach, "Beach", cafe, "Cafe", market, "Market",
            spare, "Spice farm");
    private final Set<UUID> shortlisted = titles.keySet();

    private Layout layout() {
        return Layout.of(List.of(List.of(fort, beach), List.of(cafe), List.of(market)));
    }

    @Test
    void moveBetweenDays() {
        Layout l = layout();
        PlanEdit move = new PlanEdit(PlanEdit.Type.MOVE, fort, null, 3, 0);
        assertThat(l.check(move, shortlisted)).isEmpty();
        assertThat(l.describe(move, titles::get)).isEqualTo("Move Fort from day 1 to day 3");
        assertThat(l.apply(move)).containsExactly(1, 3);
        assertThat(l.day(1)).containsExactly(beach);
        assertThat(l.day(3)).containsExactly(fort, market);
    }

    @Test
    void reorderWithinADay() {
        Layout l = layout();
        PlanEdit later = new PlanEdit(PlanEdit.Type.MOVE, fort, null, 1, null);
        assertThat(l.describe(later, titles::get)).isEqualTo("Move Fort to stop 2 on day 1");
        assertThat(l.apply(later)).containsExactly(1);
        assertThat(l.day(1)).containsExactly(beach, fort);
    }

    @Test
    void swapRemoveAndAdd() {
        Layout l = layout();
        PlanEdit swap = new PlanEdit(PlanEdit.Type.SWAP, beach, cafe, null, null);
        assertThat(l.describe(swap, titles::get)).isEqualTo("Swap Beach (day 1) with Cafe (day 2)");
        l.apply(swap);
        assertThat(l.day(1)).containsExactly(fort, cafe);
        assertThat(l.day(2)).containsExactly(beach);

        PlanEdit remove = new PlanEdit(PlanEdit.Type.REMOVE, market, null, null, null);
        assertThat(l.describe(remove, titles::get)).isEqualTo("Remove Market from day 3");
        l.apply(remove);
        assertThat(l.day(3)).isEmpty();

        PlanEdit add = new PlanEdit(PlanEdit.Type.ADD, spare, null, 3, null);
        assertThat(l.check(add, shortlisted)).isEmpty();
        assertThat(l.describe(add, titles::get)).isEqualTo("Add Spice farm to day 3");
        l.apply(add);
        assertThat(l.day(3)).containsExactly(spare);
    }

    @Test
    void editsThatNoLongerFitThePlanAreRefused() {
        Layout l = layout();
        UUID unknown = UUID.randomUUID();
        assertThat(l.check(new PlanEdit(PlanEdit.Type.MOVE, unknown, null, 9, 0), shortlisted))
                .containsExactly("That place is not on the itinerary any more.", "There is no day 9.");
        assertThat(l.check(new PlanEdit(PlanEdit.Type.ADD, fort, null, 2, null), shortlisted))
                .containsExactly("That place is already on day 1.");
        assertThat(l.check(new PlanEdit(PlanEdit.Type.ADD, unknown, null, 2, null), shortlisted))
                .containsExactly("Only shortlisted places with a location can be added.");
        assertThat(l.check(new PlanEdit(PlanEdit.Type.SWAP, fort, fort, null, null), shortlisted))
                .containsExactly("Choose two different places.");
    }
}
