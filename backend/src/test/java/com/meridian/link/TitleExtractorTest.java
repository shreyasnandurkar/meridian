package com.meridian.link;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TitleExtractorTest {

    @Test
    void prefersOpenGraphTitleWhateverTheAttributeOrder() {
        String html = """
                <html><head><title>Fallback</title>
                <meta content='Casa Susegad &amp; Gardens' property="og:title">
                </head></html>""";
        assertThat(TitleExtractor.extract(html)).isEqualTo("Casa Susegad & Gardens");
    }

    @Test
    void fallsBackToTwitterThenTitleTag() {
        assertThat(TitleExtractor.extract("<meta name=\"twitter:title\" content=\"Tweet title\"><title>T</title>"))
                .isEqualTo("Tweet title");
        assertThat(TitleExtractor.extract("<TITLE lang=en>\n  Fontainhas &#8211; Latin Quarter\n</TITLE>"))
                .isEqualTo("Fontainhas – Latin Quarter");
    }

    @Test
    void noTitleGivesNull() {
        assertThat(TitleExtractor.extract("<html><body>hi</body></html>")).isNull();
        assertThat(TitleExtractor.extract("<title>   </title>")).isNull();
    }

    @Test
    void longTitlesAreCut() {
        String title = TitleExtractor.extract("<title>" + "a".repeat(500) + "</title>");
        assertThat(title).hasSize(TitleExtractor.MAX_TITLE).endsWith("…");
    }

    @Test
    void decodesEntities() {
        assertThat(TitleExtractor.decode("&lt;b&gt; &#x263A; &quot;q&quot; &unknown;"))
                .isEqualTo("<b> ☺ \"q\" &unknown;");
    }
}
