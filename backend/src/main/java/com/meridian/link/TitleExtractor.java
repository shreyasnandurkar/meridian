package com.meridian.link;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pulls a page title out of HTML: og:title, then twitter:title, then {@code <title>}. */
final class TitleExtractor {

    static final int MAX_TITLE = 300;

    private static final Pattern META = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile(
            "([a-zA-Z:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern TITLE = Pattern.compile("<title\\b[^>]*>(.*?)</title\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);");
    private static final Map<String, String> NAMED = Map.of(
            "amp", "&", "lt", "<", "gt", ">", "quot", "\"", "apos", "'", "nbsp", " ", "ndash", "–", "mdash", "—",
            "rsquo", "’", "lsquo", "‘");

    private TitleExtractor() {}

    static String extract(String html) {
        String og = null;
        String twitter = null;
        Matcher meta = META.matcher(html);
        while (meta.find()) {
            String key = null;
            String content = null;
            Matcher attr = ATTR.matcher(meta.group());
            while (attr.find()) {
                String name = attr.group(1).toLowerCase(Locale.ROOT);
                String value = attr.group(2) != null ? attr.group(2)
                        : attr.group(3) != null ? attr.group(3) : attr.group(4);
                if (name.equals("property") || name.equals("name")) key = value.toLowerCase(Locale.ROOT);
                else if (name.equals("content")) content = value;
            }
            if (content == null || key == null) continue;
            if (og == null && key.equals("og:title")) og = clean(content);
            if (twitter == null && key.equals("twitter:title")) twitter = clean(content);
        }
        if (og != null) return og;
        if (twitter != null) return twitter;
        Matcher title = TITLE.matcher(html);
        return title.find() ? clean(title.group(1)) : null;
    }

    private static String clean(String raw) {
        String s = decode(raw).replaceAll("\\s+", " ").strip();
        if (s.isEmpty()) return null;
        return s.length() <= MAX_TITLE ? s : s.substring(0, MAX_TITLE - 1).strip() + "…";
    }

    static String decode(String s) {
        Matcher m = ENTITY.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String e = m.group(1);
            String replacement;
            try {
                if (e.startsWith("#x") || e.startsWith("#X")) {
                    replacement = Character.toString(Integer.parseInt(e.substring(2), 16));
                } else if (e.startsWith("#")) {
                    replacement = Character.toString(Integer.parseInt(e.substring(1)));
                } else {
                    replacement = NAMED.getOrDefault(e.toLowerCase(Locale.ROOT), m.group());
                }
            } catch (IllegalArgumentException ex) {
                replacement = m.group();
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
