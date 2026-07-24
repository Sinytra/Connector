package org.sinytra.connector.transformer;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

public record DependencyConfiguration(Collection<String> knownMods, Map<String, Collection<String>> aliases) {
    @Nullable
    public String findAlternative(String modId) {
        for (Entry<String, Collection<String>> entry : this.aliases.entrySet()) {
            if (entry.getValue().contains(modId) && this.knownMods.contains(entry.getKey())) {
                return entry.getKey();
            }
        }
        return null;
    }
}
