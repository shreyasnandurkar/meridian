package com.meridian.recommend;

import static com.meridian.fit.Profiles.profile;
import static org.assertj.core.api.Assertions.assertThat;

import com.meridian.fit.FitScorer.FitMember;
import com.meridian.fit.OsmTouristTrapScorer;
import com.meridian.place.OsmTags;
import com.meridian.place.PlaceCategory;
import com.meridian.place.PlaceDtos.PlaceView;
import com.meridian.recommend.CandidateScorer.Candidate;
import com.meridian.recommend.RecommendationService.Explanations;
import com.meridian.recommend.RecommendationService.Rerank;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationLogicTest {

    private static PlaceView place(String name, double lat, Map<String, String> tags) {
        return new PlaceView(UUID.nameUUIDFromBytes(name.getBytes()), "osm", "node/" + name.hashCode(), null, name,
                OsmTags.categoryOf(tags), lat, 73.8, null, null, tags, OsmTags.popularity(tags), Instant.now());
    }

    private final PlaceView famous = place("Fort Aguada", 15.49, Map.of("historic", "fort", "tourism", "attraction",
            "wikipedia", "en:Fort Aguada", "wikidata", "Q1"));
    private final PlaceView quiet = place("Cabo de Rama", 15.09, Map.of("historic", "fort"));
    private final PlaceView chain = place("Starbucks", 15.5, Map.of("amenity", "cafe", "brand", "Starbucks"));

    private final List<FitMember> group = List.of(
            profile().interests("history", 1.0, "cafes", 0.5).member("Asha"),
            profile().interests("history", 1.0, "cafes", 0.5).member("Dev"));

    private List<Candidate> rank(int gems, boolean avoidCrowds) {
        return CandidateScorer.rank(List.of(famous, quiet, chain), Set.of(), group, new OsmTouristTrapScorer(), gems,
                avoidCrowds, 15.5, 73.8);
    }

    @Test
    void hiddenGemsSliderFlipsTheOrder() {
        assertThat(rank(100, false)).extracting(c -> c.place().name()).startsWith("Cabo de Rama");
        assertThat(rank(0, false)).extracting(c -> c.place().name()).startsWith("Fort Aguada");
    }

    @Test
    void avoidCrowdsDropsChainsAndExcludedPlaces() {
        assertThat(rank(50, true)).extracting(c -> c.place().name()).doesNotContain("Starbucks");
        List<Candidate> without = CandidateScorer.rank(List.of(famous, quiet), Set.of(quiet.id()), group,
                new OsmTouristTrapScorer(), 50, false, null, null);
        assertThat(without).extracting(c -> c.place().name()).containsExactly("Fort Aguada");
    }

    @Test
    void rulesWhyNamesWhoItSuitsAndTheLocalSignal() {
        Candidate cabo = rank(100, false).getFirst();
        assertThat(cabo.rulesWhy()).isEqualTo(
                "Offers history & heritage, which suits everyone. Heritage few visitors know.");
        assertThat(cabo.distanceKm()).isGreaterThan(40);
    }

    @Test
    void groupGemsDefaultsToTheAverage() {
        assertThat(CandidateScorer.groupGems(List.of(profile().gems(80).member("A"), profile().gems(40).member("B"))))
                .isEqualTo(60);
        assertThat(CandidateScorer.groupGems(List.of())).isEqualTo(50);
    }

    @Test
    void rankingMustBeAPermutationOfTheGivenIds() {
        List<String> ids = List.of("c1", "c2", "c3");
        assertThat(RecommendationService.validateRanking(new Rerank(List.of("c2", "c3", "c1")), ids)).isEmpty();
        assertThat(RecommendationService.validateRanking(new Rerank(List.of("c2", "c2", "c9")), ids))
                .containsExactly("id \"c2\" appears twice", "unknown id \"c9\"", "missing id \"c1\"",
                        "missing id \"c3\"");
    }

    @Test
    void explanationsMayNotClaimWhatOnlyTheGroupLikes() {
        String facts = "c1 | Kegdole Beach | offers: beaches | suits: Asha | local signals: independent and little-known";

        assertThat(RecommendationService.unoffered("A little-known beach for Asha.", facts)).isEmpty();
        assertThat(RecommendationService.unoffered("A little-known beach with local food.", facts))
                .containsExactly("local food");
    }

    @Test
    void theExplainStepSeesSettingsButNotWhoLikesWhat() {
        String group = "2 travelers with preferences. Liked by everyone: local food, beaches. Also liked: nightlife "
                + "(Dev). Dealbreakers: loud clubs (Asha). Hidden-gems setting: 65/100 (prefers lesser-known places). "
                + "Avoid crowds: no.";

        assertThat(RecommendationService.settingsText(group)).isEqualTo("2 travelers with preferences. "
                + "Hidden-gems setting: 65/100 (prefers lesser-known places). Avoid crowds: no.");
    }

    @Test
    void theModelCannotMoveAPlaceFarFromItsCodeRank() {
        List<String> code = List.of("c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "c10");
        List<String> rotated = List.of("c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "c10", "c1");

        List<String> repaired = RecommendationService.boundMoves(rotated, code);

        assertThat(repaired).containsExactlyInAnyOrderElementsOf(code);
        assertThat(repaired.indexOf("c1")).isLessThanOrEqualTo(RecommendationService.MAX_MOVE);
        assertThat(repaired.subList(0, 3)).containsExactly("c2", "c3", "c4");
        assertThat(RecommendationService.boundMoves(List.of("c2", "c1", "c3"), List.of("c1", "c2", "c3")))
                .containsExactly("c2", "c1", "c3");
    }

    @Test
    void explanationsMustCoverTheBatchWithoutInventedNumbers() {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("c3", "c3 | Cabo de Rama | offers: history | suits: Asha");
        facts.put("c4", "c4 | Fort | offers: history");
        String groupText = "2 travelers with preferences. Hidden-gems setting: 80/100.";
        var ok = new Explanations(List.of(new Explanations.Item("c3", "A quiet fort for Asha."),
                new Explanations.Item("c4", "History for both of you, the 2 of you.")));
        assertThat(RecommendationService.validateExplanations(ok, facts, groupText)).isEmpty();

        var bad = new Explanations(List.of(new Explanations.Item("c3", "Built in 1540, a hidden gem (3 stars)."),
                new Explanations.Item("c7", "x")));
        assertThat(RecommendationService.validateExplanations(bad, facts, groupText)).containsExactlyInAnyOrder(
                "c3: the number 1540 is not in the facts", "c3: the number 3 is not in the facts",
                "unknown id \"c7\"", "missing item for id \"c4\"");
    }

    @Test
    void groupTextSummarisesSharedAndIndividualInterests() {
        String text = RecommendationService.groupText(List.of(
                profile().interests("history", 1.0, "beaches", 0.75).dealbreakers("clubs").member("Asha"),
                profile().interests("history", 0.75).member("Dev")), 80, true);
        assertThat(text).isEqualTo("2 travelers with preferences. Liked by everyone: history & heritage. "
                + "Also liked: beaches (Asha). Dealbreakers: clubs (Asha). "
                + "Hidden-gems setting: 80/100 (prefers lesser-known places). Avoid crowds: yes.");
    }
}
