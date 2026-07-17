package org.sinytra.connector.transformer.api;

import org.sinytra.adapter.transform.patch.MethodPatch;
import org.sinytra.connector.transformer.transform.MixinPatchTransformer;

import java.util.List;

/**
 * Used by plugins to register method patches, which are applied by the {@link MixinPatchTransformer}.
 */
public interface PatchRegistrar {
    int HIGHEST_SYSTEM_PRIORITY = 1000;
    int HIGHER_SYSTEM_PRIORITY = 500;
    int DEFAULT_PRIORITY = 0;
    int LOWER_SYSTEM_PRIORITY = -500;
    int LOWEST_SYSTEM_PRIORITY = -1000;

    /**
     * Register a single patch with default priority.
     *
     * @param patch patch to register
     */
    default void add(MethodPatch patch) {
        add(DEFAULT_PRIORITY, patch);
    }

    /**
     * Register a patch group with default priority.
     *
     * @param patches patches to register
     */
    default void add(List<MethodPatch> patches) {
        add(DEFAULT_PRIORITY, patches);
    }

    /**
     * Register a single patch with a given priority.
     *
     * @param priority patch priority
     * @param patch    patch to register
     */
    void add(int priority, MethodPatch patch);

    /**
     * Register a patch group with a given priority.
     *
     * @param priority patch priority
     * @param patches  patch to register
     */
    void add(int priority, List<MethodPatch> patches);
}
