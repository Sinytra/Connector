package org.sinytra.connector.transformer.api;

import net.neoforged.art.api.Transformer;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface TransformerRegistrar {
    enum OrderingHint {
        EARLY,
        DEFAULT,
        LATE
    }

    default void register(String name, Transformer transformer) {
        register(name, null, null, transformer);
    }

    void registerBefore(String name, Set<String> runsBefore, Transformer transformer);

    void registerAfter(String name, Set<String> runsAfter, Transformer transformer);

    default void register(String name, @Nullable Set<String> runsBefore, @Nullable Set<String> runsAfter, Transformer transformer) {
        register(name, runsBefore, runsAfter, OrderingHint.DEFAULT, transformer);
    }

    void register(String name, @Nullable Set<String> runsBefore, @Nullable Set<String> runsAfter, OrderingHint ordering, Transformer transformer);
}
