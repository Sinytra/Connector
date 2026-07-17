package org.sinytra.connector.transformer.plugin;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.mojang.logging.LogUtils;
import net.neoforged.art.api.Transformer;
import net.neoforged.fml.loading.toposort.TopologicalSort;
import org.jetbrains.annotations.Nullable;
import org.sinytra.connector.transformer.api.TransformerRegistrar;
import org.slf4j.Logger;

import java.util.*;

@SuppressWarnings("UnstableApiUsage")
public class TransformerRegistrarImpl implements TransformerRegistrar {
    private static final Logger LOGGER = LogUtils.getLogger();

    private record Registration(String name, Set<String> runsBefore, Set<String> runsAfter, OrderingHint ordering, Transformer transformer) {
    }

    private final List<Registration> registrations = new ArrayList<>();

    @Override
    public void registerBefore(String name, Set<String> runsBefore, Transformer transformer) {
        Objects.requireNonNull(runsBefore, "runsBefore must not be null");
        register(name, runsBefore, null, transformer);
    }

    @Override
    public void registerAfter(String name, Set<String> runsAfter, Transformer transformer) {
        Objects.requireNonNull(runsAfter, "runsAfter must not be null");
        register(name, null, runsAfter, transformer);
    }

    @Override
    public void register(String name, @Nullable Set<String> runsBefore, @Nullable Set<String> runsAfter, OrderingHint ordering, Transformer transformer) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(ordering, "ordering must not be null");
        Objects.requireNonNull(transformer, "transformer must not be null");

        Registration registration = new Registration(
            name,
            Objects.requireNonNullElse(runsBefore, Set.of()),
            Objects.requireNonNullElse(runsAfter, Set.of()),
            ordering,
            transformer
        );

        this.registrations.add(registration);
    }

    public List<Transformer> getSortedTransformers() {
        HashMap<String, Registration> transformers = new HashMap<>();
        MutableGraph<Registration> graph = GraphBuilder.directed().build();

        for (var transformer : this.registrations) {
            if (transformers.containsKey(transformer.name())) {
                LOGGER.error(
                    "Duplicate transformers with name {}, of types {} and {}",
                    transformer.name(),
                    transformers.get(transformer.name()).getClass().getName(),
                    transformer.getClass().getName()
                );
                throw new IllegalStateException("Duplicate transformers with name: " + transformer.name());
            }

            graph.addNode(transformer);
            transformers.put(transformer.name(), transformer);
        }

        for (Registration self : transformers.values()) {
            // If the targeted transformer is not present, then the ordering does not matter;
            // this allows for e.g. ordering with transformers that may or may not be present.
            for (var targetName : self.runsBefore()) {
                var target = transformers.get(targetName);
                if (target == self) {
                    continue;
                }
                if (target != null) {
                    graph.putEdge(self, target);
                }
            }
            for (var targetName : self.runsAfter()) {
                var target = transformers.get(targetName);
                if (target == self) {
                    continue;
                }
                if (target != null) {
                    graph.putEdge(target, self);
                }
            }
        }
        List<Registration> sorted = TopologicalSort.topologicalSort(
            graph,
            Comparator.comparing(Registration::ordering).thenComparing(Registration::name)
        );
        return sorted.stream()
            .map(Registration::transformer)
            .toList();
    }
}
