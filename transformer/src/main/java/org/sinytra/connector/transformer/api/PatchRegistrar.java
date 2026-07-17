package org.sinytra.connector.transformer.api;

import org.sinytra.adapter.transform.patch.MethodPatch;

import java.util.List;

public interface PatchRegistrar {
    int DEFAULT_PRIORITY = 0;
    int HIGHER_SYSTEM_PRIORITY = 500;
    int HIGHEST_SYSTEM_PRIORITY = 1000;
    int LOWER_SYSTEM_PRIORITY = -500;
    int LOWEST_SYSTEM_PRIORITY = -1000;

    default void add(MethodPatch patch) {
        add(DEFAULT_PRIORITY, patch);
    }

    default void add(List<MethodPatch> patches) {
        add(DEFAULT_PRIORITY, patches);
    }

    void add(int priority, MethodPatch patch);

    void add(int priority, List<MethodPatch> patches);
}
