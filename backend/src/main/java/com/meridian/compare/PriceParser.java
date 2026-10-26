package com.meridian.compare;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds a price like "₹4,500", "Rs. 1200" or "USD 89.50" in a page title. */
final class PriceParser {

    record Price(BigDecimal amount, String currency) {}

    private static final Map<String, String> SYMBOLS = Map.of(
            "₹", "INR", "rs", "INR", "rs.", "INR", "$", "USD", "€", "EUR", "£", "GBP");

    private static final Pattern PRICE = Pattern.compile(
            "(₹|rs\\.?|inr|\\$|usd|€|eur|£|gbp)\\s?(\\d{1,3}(?:,\\d{2,3})+|\\d+)(\\.\\d{1,2})?",
            Pattern.CASE_INSENSITIVE);

    private PriceParser() {}

    static Optional<Price> find(String text) {
        if (text == null) return Optional.empty();
        Matcher m = PRICE.matcher(text);
        if (!m.find()) return Optional.empty();
        String unit = m.group(1).toLowerCase(Locale.ROOT);
        String currency = SYMBOLS.getOrDefault(unit, unit.toUpperCase(Locale.ROOT));
        String digits = m.group(2).replace(",", "") + (m.group(3) == null ? "" : m.group(3));
        return Optional.of(new Price(new BigDecimal(digits), currency));
    }
}
