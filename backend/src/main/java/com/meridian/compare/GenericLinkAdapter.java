package com.meridian.compare;

import java.net.URI;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Any other website: the provider is the site's host name. */
@Component
@Order(100)
public class GenericLinkAdapter implements ProviderAdapter {

    @Override
    public String id() {
        return "link";
    }

    @Override
    public boolean supports(URI uri) {
        return uri.getHost() != null;
    }

    @Override
    public OptionDraft draft(URI uri, String pageTitle) {
        String host = uri.getHost().replaceFirst("^www\\.", "");
        var price = PriceParser.find(pageTitle);
        return new OptionDraft(host, pageTitle, price.map(PriceParser.Price::amount).orElse(null),
                price.map(PriceParser.Price::currency).orElse(null), uri.toString(), id());
    }
}
