package org.sinytra.connector.transformer.patch;

import java.util.HashMap;
import java.util.Map;

public class SimpleRefmap {
    public final Map<String, Map<String, String>> mappings;
    public final Map<String, Map<String, Map<String, String>>> data;

    @SuppressWarnings("unused")
    public SimpleRefmap() {
        this.mappings = new HashMap<>();
        this.data = new HashMap<>();
    }

    public SimpleRefmap(Map<String, Map<String, String>> mappings, Map<String, Map<String, Map<String, String>>> data) {
        this.mappings = mappings;
        this.data = data;
    }

    public SimpleRefmap merge(SimpleRefmap other) {
        Map<String, Map<String, String>> mergeMappings = new HashMap<>(this.mappings);
        mergeMappings.putAll(other.mappings);
        Map<String, Map<String, Map<String, String>>> mergeData = new HashMap<>(this.data);
        mergeData.putAll(other.data);
        return new SimpleRefmap(mergeMappings, mergeData);
    }
}
