package com.meridian.fit;

import static org.assertj.core.api.Assertions.assertThat;

import com.meridian.fit.GroupFitModels.Row;
import com.meridian.fit.GroupFitModels.Status;
import com.meridian.fit.TradeoffWriter.Output;
import com.meridian.fit.TradeoffWriter.Output.Item;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradeoffWriterTest {

    private final Row crowds = new Row("crowds", "Crowds", Status.CONFLICT, List.of(), "Asha is at 20 and Dev at 90.",
            "Visit famous spots early.", List.of("crowd tolerance ranges from 20 (Asha) to 90 (Dev)",
                    "spread 70 points out of 100"));
    private final Row pace = new Row("pace", "Pace", Status.TRADE_OFF, List.of(), "x", "Keep days light.",
            List.of("pace slow: Asha", "pace balanced: Meera"));
    private final Row agree = new Row("gems", "Gems", Status.AGREE, List.of(), "x", null, List.of());

    @Test
    void acceptsGroundedOutput() {
        Output ok = new Output(List.of(
                new Item("crowds", "Quiet vs. buzz", "Asha prefers quiet places, Dev enjoys a crowd (90 of 100).",
                        "Go early."),
                new Item("pace", "Slow vs. balanced", "Asha wants slow days.", "Keep afternoons free.")));
        assertThat(TradeoffWriter.validate(ok, List.of(crowds, pace))).isEmpty();
    }

    @Test
    void rejectsInventedNumbersUnknownAndMissingKeys() {
        Output bad = new Output(List.of(
                new Item("crowds", "Quiet vs. buzz", "Asha is 3 times quieter.", "Go early."),
                new Item("budget", "Money", "x", "y")));
        assertThat(TradeoffWriter.validate(bad, List.of(crowds, pace))).containsExactlyInAnyOrder(
                "crowds: the number 3 is not in the facts",
                "unknown key \"budget\"",
                "missing item for key \"pace\"");
    }

    @Test
    void rejectsEmptyAndOverlongText() {
        Output bad = new Output(List.of(new Item("pace", "", "x".repeat(301), "ok")));
        assertThat(TradeoffWriter.validate(bad, List.of(pace)))
                .contains("pace: headline is empty", "pace: explanation is longer than 300 characters");
    }

    @Test
    void templateCoversOnlyRowsThatNeedPhrasing() {
        var summary = TradeoffWriter.template(List.of(crowds, pace, agree), "k", "note");
        assertThat(summary.items()).extracting(GroupFitModels.Tradeoff::key).containsExactly("crowds", "pace");
        assertThat(summary.items().getFirst().headline()).isEqualTo("Crowds: conflict");
        assertThat(summary.provenance().source()).isEqualTo("rules");
    }

    @Test
    void promptInputListsFactsAndSuggestion() {
        assertThat(TradeoffWriter.render(List.of(pace))).isEqualTo("""
                [pace] Pace (trade-off)
                facts: pace slow: Asha | pace balanced: Meera
                code suggestion: Keep days light.""");
    }
}
