package com.meridian.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Loads and caches prompts from {@code classpath:prompts/<name>.md}. */
@Component
public class PromptLibrary {

    private final Map<String, PromptTemplate> cache = new ConcurrentHashMap<>();

    public PromptTemplate get(String name) {
        return cache.computeIfAbsent(name, PromptLibrary::load);
    }

    private static PromptTemplate load(String name) {
        ClassPathResource resource = new ClassPathResource("prompts/" + name + ".md");
        try (InputStream in = resource.getInputStream()) {
            return PromptTemplate.parse(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Missing prompt " + name, e);
        }
    }
}
