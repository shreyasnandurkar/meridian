package com.meridian.compare;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Well-known booking sites: gives them their proper name and trims the site name off page titles. */
@Component
@Order(1)
public class KnownProviderAdapter implements ProviderAdapter {

    /** Host (or host + path) → display name. Checked in order. */
    static final Map<String, String> PROVIDERS = new LinkedHashMap<>();

    static {
        PROVIDERS.put("google.com/travel", "Google Travel");
        PROVIDERS.put("booking.com", "Booking.com");
        PROVIDERS.put("airbnb", "Airbnb");
        PROVIDERS.put("agoda.com", "Agoda");
        PROVIDERS.put("makemytrip.com", "MakeMyTrip");
        PROVIDERS.put("goibibo.com", "Goibibo");
        PROVIDERS.put("cleartrip.com", "Cleartrip");
        PROVIDERS.put("ixigo.com", "ixigo");
        PROVIDERS.put("skyscanner", "Skyscanner");
        PROVIDERS.put("expedia", "Expedia");
        PROVIDERS.put("hotels.com", "Hotels.com");
        PROVIDERS.put("hostelworld.com", "Hostelworld");
        PROVIDERS.put("zostel.com", "Zostel");
        PROVIDERS.put("tripadvisor", "Tripadvisor");
        PROVIDERS.put("getyourguide", "GetYourGuide");
        PROVIDERS.put("klook.com", "Klook");
        PROVIDERS.put("viator.com", "Viator");
        PROVIDERS.put("irctc.co.in", "IRCTC");
        PROVIDERS.put("redbus.in", "redBus");
        PROVIDERS.put("goindigo.in", "IndiGo");
        PROVIDERS.put("airindia.com", "Air India");
    }

    @Override
    public String id() {
        return "known-provider";
    }

    @Override
    public boolean supports(URI uri) {
        return provider(uri).isPresent();
    }

    @Override
    public OptionDraft draft(URI uri, String pageTitle) {
        String name = provider(uri).orElseThrow();
        String title = pageTitle == null ? null : trimSiteName(pageTitle, name);
        var price = PriceParser.find(pageTitle);
        return new OptionDraft(name, title, price.map(PriceParser.Price::amount).orElse(null),
                price.map(PriceParser.Price::currency).orElse(null), uri.toString(), id());
    }

    /** Matches whole host labels, so "notbooking.com" is not Booking.com but "www.airbnb.co.in" is Airbnb. */
    static Optional<String> provider(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        String dotted = "." + host + ".";
        return PROVIDERS.entrySet().stream()
                .filter(e -> {
                    String key = e.getKey();
                    int slash = key.indexOf('/');
                    if (slash > 0) {
                        String h = key.substring(0, slash);
                        return (host.equals(h) || host.endsWith("." + h)) && path.startsWith(key.substring(slash));
                    }
                    return dotted.contains("." + key + ".");
                })
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /** "Casa Susegad, Loutolim | Booking.com" → "Casa Susegad, Loutolim". */
    static String trimSiteName(String title, String provider) {
        String t = title.strip();
        String lower = t.toLowerCase(Locale.ROOT);
        String p = provider.toLowerCase(Locale.ROOT).replace(".com", "");
        for (String sep : new String[] {" | ", " - ", " – ", " — ", " · "}) {
            int i = lower.lastIndexOf(sep);
            if (i > 0 && lower.substring(i + sep.length()).contains(p)) return t.substring(0, i).strip();
        }
        return t;
    }
}
