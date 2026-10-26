package com.meridian.compare;

import java.math.BigDecimal;
import java.net.URI;

/**
 * Turns a pasted provider link into a draft comparison row (spec §5.6). Adapters only read what the public page
 * says; they never book or sign in. New providers (or a real price API) plug in by adding an adapter bean.
 */
public interface ProviderAdapter {

    /**
     * @param price   null when the page doesn't show one; the member types it in
     * @param adapter which adapter produced the draft
     */
    record OptionDraft(String provider, String title, BigDecimal price, String currency, String sourceUrl,
            String adapter) {}

    String id();

    boolean supports(URI uri);

    OptionDraft draft(URI uri, String pageTitle);
}
