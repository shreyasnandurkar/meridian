package com.meridian.compare;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import org.junit.jupiter.api.Test;

class ProviderAdapterTest {

    private final KnownProviderAdapter known = new KnownProviderAdapter();
    private final GenericLinkAdapter generic = new GenericLinkAdapter();

    @Test
    void recognisesKnownProvidersByWholeHostLabel() {
        assertThat(KnownProviderAdapter.provider(URI.create("https://www.booking.com/hotel/in/casa.html")))
                .hasValue("Booking.com");
        assertThat(KnownProviderAdapter.provider(URI.create("https://www.airbnb.co.in/rooms/1"))).hasValue("Airbnb");
        assertThat(KnownProviderAdapter.provider(URI.create("https://www.google.com/travel/flights?q=x")))
                .hasValue("Google Travel");
        assertThat(KnownProviderAdapter.provider(URI.create("https://www.google.com/search?q=x"))).isEmpty();
        assertThat(KnownProviderAdapter.provider(URI.create("https://notbooking.com/x"))).isEmpty();
        assertThat(known.supports(URI.create("https://example.org"))).isFalse();
    }

    @Test
    void draftTrimsTheSiteNameAndReadsAPrice() {
        var d = known.draft(URI.create("https://www.booking.com/hotel/in/casa.html"),
                "Casa Susegad, Loutolim – from ₹4,500 | Booking.com");
        assertThat(d.provider()).isEqualTo("Booking.com");
        assertThat(d.title()).isEqualTo("Casa Susegad, Loutolim – from ₹4,500");
        assertThat(d.price()).isEqualByComparingTo(new BigDecimal("4500"));
        assertThat(d.currency()).isEqualTo("INR");
        assertThat(d.adapter()).isEqualTo("known-provider");
    }

    @Test
    void genericLinksUseTheHostAndMayHaveNoPrice() {
        var d = generic.draft(URI.create("https://www.goa-kayaks.in/tours"), "Sunrise kayak tours");
        assertThat(d.provider()).isEqualTo("goa-kayaks.in");
        assertThat(d.price()).isNull();
        assertThat(d.currency()).isNull();
    }

    @Test
    void priceParserHandlesCommonFormats() {
        assertThat(PriceParser.find("Rs. 1200 per night")).hasValueSatisfying(p -> {
            assertThat(p.amount()).isEqualByComparingTo("1200");
            assertThat(p.currency()).isEqualTo("INR");
        });
        assertThat(PriceParser.find("Only USD 89.50!")).hasValueSatisfying(p -> {
            assertThat(p.amount()).isEqualByComparingTo("89.50");
            assertThat(p.currency()).isEqualTo("USD");
        });
        assertThat(PriceParser.find("₹1,25,000 villa")).hasValueSatisfying(
                p -> assertThat(p.amount()).isEqualByComparingTo("125000"));
        assertThat(PriceParser.find("Best stays in Goa 2026")).isEmpty();
    }
}
