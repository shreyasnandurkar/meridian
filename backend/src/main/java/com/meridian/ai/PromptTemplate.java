package com.meridian.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A versioned prompt loaded from {@code resources/prompts/*.md}.
 *
 * <p>Format: a front-matter block ({@code version}, {@code temperature}) followed by sections headed
 * {@code ## system}, {@code ## user} or {@code ## assistant}. Sections become chat messages in order, so few-shot
 * examples are written as alternating user/assistant sections. {@code {{name}}} placeholders are filled at render time.
 */
public record PromptTemplate(String name, String version, double temperature, List<Message> messages) {

    public record Message(String role, String content) {}

    private static final Pattern HEADING = Pattern.compile("^## (system|user|assistant)\\s*$");
    private static final Pattern VAR = Pattern.compile("\\{\\{(\\w+)}}");

    public static PromptTemplate parse(String name, String source) {
        String text = source.replace("\r\n", "\n");
        if (!text.startsWith("---\n")) {
            throw new IllegalArgumentException("Prompt " + name + " is missing front matter");
        }
        int end = text.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new IllegalArgumentException("Prompt " + name + " has unterminated front matter");
        }
        String version = null;
        double temperature = 0.3;
        for (String line : text.substring(4, end).split("\n")) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "version" -> version = value;
                case "temperature" -> temperature = Double.parseDouble(value);
                default -> { }
            }
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Prompt " + name + " has no version");
        }

        List<Message> messages = new ArrayList<>();
        String role = null;
        StringBuilder body = new StringBuilder();
        for (String line : text.substring(end + 5).split("\n", -1)) {
            Matcher m = HEADING.matcher(line);
            if (m.matches()) {
                if (role != null) messages.add(new Message(role, body.toString().strip()));
                role = m.group(1);
                body.setLength(0);
            } else if (role != null) {
                body.append(line).append('\n');
            }
        }
        if (role != null) messages.add(new Message(role, body.toString().strip()));
        if (messages.isEmpty() || !"user".equals(messages.getLast().role())) {
            throw new IllegalArgumentException("Prompt " + name + " must end with a user section");
        }
        return new PromptTemplate(name, version, temperature, List.copyOf(messages));
    }

    /** Fills placeholders; unknown placeholders are an error so a typo never reaches the model. */
    public List<Message> render(Map<String, String> vars) {
        return messages.stream().map(msg -> new Message(msg.role(), fill(msg.content(), vars))).toList();
    }

    private String fill(String content, Map<String, String> vars) {
        Matcher m = VAR.matcher(content);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = vars.get(m.group(1));
            if (value == null) {
                throw new IllegalArgumentException("Prompt " + name + " needs variable " + m.group(1));
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }
}
