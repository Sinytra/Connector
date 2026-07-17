package org.sinytra.connector.transformer.api;

import net.neoforged.art.api.Transformer;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Used to register ART {@link Transformer transformers} to the Connector jar transformation pipeline.
 */
public interface TransformerRegistrar {
    /**
     * General purpose ordering hint for sorting transformers without named dependencies.
     */
    enum OrderingHint {
        EARLY,
        DEFAULT,
        LATE
    }

    /**
     * Register a transformer with no specific order.
     *
     * @param name        globally unique identifier for the transformer
     * @param transformer the transformer to register
     */
    default void register(String name, Transformer transformer) {
        register(name, null, null, transformer);
    }

    /**
     * Register a transformer that runs before others.
     *
     * @param name        globally unique identifier for the transformer
     * @param runsBefore  a set of transformer IDs that will run after this transformer
     * @param transformer the transformer to register
     */
    void registerBefore(String name, Set<String> runsBefore, Transformer transformer);

    /**
     * Register a transformer that runs after others.
     *
     * @param name        globally unique identifier for the transformer
     * @param runsAfter   a set of transformer IDs that will run before this transformer
     * @param transformer the transformer to register
     */
    void registerAfter(String name, Set<String> runsAfter, Transformer transformer);

    /**
     * Register a transformer with optional dependencies but no preferred ordering.
     *
     * @param name        globally unique identifier for the transformer
     * @param runsBefore  a set of transformer IDs that will run after this transformer. nullable.
     * @param runsAfter   a set of transformer IDs that will run before this transformer. nullable.
     * @param transformer the transformer to register
     */
    default void register(String name, @Nullable Set<String> runsBefore, @Nullable Set<String> runsAfter, Transformer transformer) {
        register(name, runsBefore, runsAfter, OrderingHint.DEFAULT, transformer);
    }

    /**
     * Register a transformer with optional dependencies and an ordering hint.
     *
     * @param name        globally unique identifier for the transformer
     * @param runsBefore  a set of transformer IDs that will run after this transformer
     * @param runsAfter   a set of transformer IDs that will run before this transformer
     * @param ordering    ordering hint for sorting transformers outside dependencies
     * @param transformer the transformer to register
     */
    void register(String name, @Nullable Set<String> runsBefore, @Nullable Set<String> runsAfter, OrderingHint ordering, Transformer transformer);
}
