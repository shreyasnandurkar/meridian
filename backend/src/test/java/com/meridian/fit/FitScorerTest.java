package com.meridian.fit;

import static com.meridian.fit.Profiles.profile;
import static org.assertj.core.api.Assertions.assertThat;

import com.meridian.fit.FitScorer.Effect;
import com.meridian.fit.FitScorer.FitMember;
import com.meridian.fit.FitScorer.MemberFit;
import com.meridian.fit.FitScorer.Verdict;
import com.meridian.place.PlaceCategory;
import com.meridian.shortlist.Reaction;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FitScorerTest {

    private final TouristTrapScorer trap = new OsmTouristTrapScorer();

    private static PlaceSignals place(String name, PlaceCategory category, int popularity, String... tags) {
        Map<String, String> t = new java.util.HashMap<>();
        for (int i = 0; i < tags.length; i += 2) t.put(tags[i], tags[i + 1]);
        return new PlaceSignals(name, category, PlaceInterests.of(t, name, category), Map.copyOf(t), popularity);
    }

    private MemberFit score(FitMember m, PlaceSignals p) {
        return FitScorer.score(m, p, trap.assess(p), null, null, null);
    }

    private static final PlaceSignals BEACH = place("Kakolem Beach", PlaceCategory.SIGHTS, 0, "natural", "beach");
    private static final PlaceSignals CLUB = place("Club Cubana", PlaceCategory.ACTIVITIES, 2, "amenity", "nightclub");

    @Test
    void foreignCuisinesAreFoodButNotLocalFood() {
        assertThat(place("Yaki Zushi", PlaceCategory.FOOD, 0, "amenity", "restaurant").strength("local_food"))
                .isLessThan(0.5);
        assertThat(place("Spice Hub", PlaceCategory.FOOD, 0, "amenity", "restaurant", "cuisine", "pizza")
                .strength("local_food")).isLessThan(0.5);
        assertThat(place("Ritz Classic", PlaceCategory.FOOD, 0, "amenity", "restaurant", "cuisine", "goan;chinese")
                .strength("local_food")).isEqualTo(1.0);
        assertThat(place("Hotel Vihar", PlaceCategory.FOOD, 0, "amenity", "restaurant").strength("local_food"))
                .isGreaterThanOrEqualTo(0.5);
    }

    @Test
    void namesWithRepeatedWordsAreScored() {
        PlaceSignals kabai = place("Kabai Kabai", PlaceCategory.FOOD, 0, "amenity", "restaurant");
        FitMember m = profile().interests("local_food", 1.0).mustHaves("kabai").dealbreakers("clubs").member("Asha");

        assertThat(score(m, kabai).score()).isNotNull();
    }

    @Test
    void interestsDriveTheScore() {
        MemberFit lover = score(profile().interests("beaches", 1.0, "nature", 0.75).member("Asha"), BEACH);
        MemberFit indifferent = score(profile().interests("beaches", 0.0, "nature", 0.0).member("Ravi"), BEACH);

        assertThat(lover.score()).isGreaterThan(indifferent.score());
        assertThat(lover.verdict()).isEqualTo(Verdict.SATISFIES);
        assertThat(indifferent.verdict()).isEqualTo(Verdict.MISSES);
        assertThat(lover.reasons()).extracting(FitScorer.Reason::text).contains("Into beaches");
        assertThat(indifferent.reasons()).extracting(FitScorer.Reason::text).contains("Not into beaches");
    }

    @Test
    void dealbreakerCapsTheScoreEvenWithMatchingInterests() {
        FitMember m = profile().interests("nightlife", 1.0, "music", 1.0).dealbreakers("loud clubs").member("Meera");
        MemberFit fit = score(m, CLUB);
        assertThat(fit.score()).isLessThanOrEqualTo(FitScorer.BLOCKER_CAP);
        assertThat(fit.reasons()).anyMatch(r -> r.effect() == Effect.BLOCKER && r.text().contains("loud clubs"));
    }

    @Test
    void mustHaveMatchingLiftsTheScore() {
        FitMember plain = profile().interests("beaches", 0.5).member("A");
        FitMember wants = profile().interests("beaches", 0.5).mustHaves("beach time").member("B");
        assertThat(score(wants, BEACH).score()).isGreaterThan(score(plain, BEACH).score());
    }

    @Test
    void hiddenGemsFansPreferUnknownPlacesAndClassicsFansFamousOnes() {
        PlaceSignals famous = place("Basilica of Bom Jesus", PlaceCategory.SIGHTS, 9, "historic", "church",
                "tourism", "attraction", "wikipedia", "en:Basilica", "wikidata", "Q1");
        PlaceSignals quiet = place("Chapel of St Anne", PlaceCategory.SIGHTS, 0, "historic", "church");
        FitMember gems = profile().interests("history", 1.0).gems(100).crowd(50).member("G");
        FitMember classics = profile().interests("history", 1.0).gems(0).crowd(50).member("C");

        assertThat(score(gems, quiet).score()).isGreaterThan(score(gems, famous).score());
        assertThat(score(classics, famous).score()).isGreaterThan(score(classics, quiet).score());
    }

    @Test
    void crowdAverseMembersArePenalisedForFamousPlaces() {
        PlaceSignals famous = place("Fort Aguada", PlaceCategory.SIGHTS, 7, "historic", "fort", "wikipedia", "x",
                "wikidata", "Q");
        MemberFit calm = score(profile().interests("history", 1.0).crowd(0).member("Calm"), famous);
        MemberFit easy = score(profile().interests("history", 1.0).crowd(60).member("Easy"), famous);
        assertThat(calm.score()).isLessThan(easy.score());
        assertThat(calm.reasons()).extracting(FitScorer.Reason::text).contains("Likely crowded; prefers quiet places");
    }

    @Test
    void reactionsOverrideTheEstimate() {
        FitMember m = profile().interests("beaches", 0.0).member("Ravi");
        PlaceSignals p = BEACH;
        MemberFit loved = FitScorer.score(m, p, trap.assess(p), Reaction.Value.LOVE, "sunsets", null);
        MemberFit refused = FitScorer.score(profile().interests("beaches", 1.0).member("Asha"), p, trap.assess(p),
                Reaction.Value.NO_WAY, null, null);
        assertThat(loved.score()).isGreaterThanOrEqualTo(90);
        assertThat(loved.reasons().getFirst().text()).isEqualTo("Loves it: “sunsets”");
        assertThat(refused.score()).isLessThanOrEqualTo(10);
        assertThat(refused.verdict()).isEqualTo(Verdict.MISSES);
    }

    @Test
    void membersWithoutProfileAreUnknownUnlessTheyReacted() {
        FitMember none = new FitMember(java.util.UUID.randomUUID(), "New", null, List.of());
        assertThat(score(none, BEACH).verdict()).isEqualTo(Verdict.UNKNOWN);
        assertThat(score(none, BEACH).score()).isNull();
        MemberFit reacted = FitScorer.score(none, BEACH, trap.assess(BEACH), Reaction.Value.OK, null, null);
        assertThat(reacted.score()).isEqualTo(60);
    }

    @Test
    void dietaryAndAccessNeedsUseOsmTags() {
        PlaceSignals seafood = place("Fisherman's Wharf", PlaceCategory.FOOD, 0, "amenity", "restaurant",
                "cuisine", "seafood");
        PlaceSignals veg = place("Vegan Vibes", PlaceCategory.FOOD, 0, "amenity", "restaurant", "diet:vegan", "only");
        FitMember vegan = profile().interests("local_food", 0.75).dietary("vegan").member("V");
        assertThat(score(vegan, veg).score()).isGreaterThan(score(vegan, seafood).score());

        PlaceSignals steps = place("Hill Fort", PlaceCategory.SIGHTS, 0, "historic", "fort", "wheelchair", "no");
        MemberFit wheel = score(profile().interests("history", 1.0).access("wheelchair").member("W"), steps);
        assertThat(wheel.score()).isLessThanOrEqualTo(FitScorer.BLOCKER_CAP);
    }

    @Test
    void stayStarsAreCheckedAgainstTheStayBudget() {
        PlaceSignals luxury = place("Taj", PlaceCategory.STAYS, 0, "tourism", "hotel", "stars", "5");
        MemberFit low = score(profile().budget("low", "mid", "mid").member("L"), luxury);
        MemberFit high = score(profile().budget("high", "mid", "mid").member("H"), luxury);
        assertThat(low.score()).isLessThan(high.score());
        assertThat(low.reasons()).extracting(FitScorer.Reason::text).contains("5-star stay; above their stay budget");
    }

    @Test
    void hitsMatchesPhrasesByInterestOrNameWord() {
        assertThat(FitScorer.hits("no clubs", CLUB)).isTrue();
        assertThat(FitScorer.hits("Kakolem", BEACH)).isTrue();
        assertThat(FitScorer.hits("museums", BEACH)).isFalse();
    }
}
