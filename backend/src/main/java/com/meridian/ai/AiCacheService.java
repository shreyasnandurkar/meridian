package com.meridian.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AI results stored per input hash (spec §4 "Result cache"). */
@Service
public class AiCacheService {

    /** Sorted keys so equal inputs always hash the same. */
    private static final ObjectMapper CANONICAL = new ObjectMapper()
            .findAndRegisterModules()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    private final AiCacheRepository repo;
    private final ObjectMapper json;

    public AiCacheService(AiCacheRepository repo, ObjectMapper json) {
        this.repo = repo;
        this.json = json;
    }

    public String key(String feature, String promptVersion, String model, Object input) {
        try {
            return Hashing.sha256(feature + '|' + promptVersion + '|' + model + '|' + CANONICAL.writeValueAsString(input));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot hash AI input", e);
        }
    }

    @Transactional(readOnly = true)
    public <T> Optional<T> get(String key, Class<T> type) {
        return repo.findById(key).map(e -> json.convertValue(e.getResult(), type));
    }

    @Transactional
    public void put(String key, String feature, String promptVersion, String model, Object result) {
        repo.save(new AiCacheEntry(key, feature, promptVersion, model, json.valueToTree(result)));
    }
}
