package dev.su5ed.sinytra.connector.transformer.jar;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.MappingResolverImpl;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static dev.su5ed.sinytra.connector.transformer.jar.JarTransformer.TRANSFORM_MARKER;

public class IntermediateMapping {
    private static final Map<String, Map<String, String>> INTERMEDIATE_MAPPINGS_CACHE = new HashMap<>();
    // Filter out non-obfuscated method names used in mapping namespaces as those don't need
    // to be remapped and will only cause issues with our barebones find/replace remapper
    private static final Map<String, Collection<String>> MAPPING_PREFIXES = Map.of(
        "intermediary", Set.of("net/minecraft/class_", "field_", "method_", "comp_")
    );
    private static final Logger LOGGER = LogUtils.getLogger();

    public static Map<String, String> get(String sourceNamespace) {
        Map<String, String> map = INTERMEDIATE_MAPPINGS_CACHE.get(sourceNamespace);
        if (map == null) {
            synchronized (JarTransformer.class) {
                Map<String, String> existing = INTERMEDIATE_MAPPINGS_CACHE.get(sourceNamespace);
                if (existing != null) {
                    return existing;
                }

                LOGGER.debug(TRANSFORM_MARKER, "Creating flat intermediate mapping for namespace {}", sourceNamespace);
                // Intermediary sometimes contains duplicate names for different methods (why?). We exclude those.
                Set<String> excludedNames = new HashSet<>();
                Collection<String> prefixes = MAPPING_PREFIXES.get(sourceNamespace);
                MappingResolverImpl resolver = FabricLoaderImpl.INSTANCE.getMappingResolver();
                Map<String, String> resolved = new HashMap<>();
                resolver.getCurrentMap(sourceNamespace).getClasses().stream()
                    .flatMap(cls -> Stream.concat(Stream.of(cls), Stream.concat(cls.getFields().stream(), cls.getMethods().stream()))
                        .filter(node -> prefixes.stream().anyMatch(node.getOriginal()::startsWith))
                        .map(node -> Pair.of(node.getOriginal(), node.getMapped())))
                    .forEach(pair -> {
                        String original = pair.getFirst();
                        if (resolved.containsKey(original)) {
                            excludedNames.add(original);
                            resolved.remove(original);
                        }
                        if (!excludedNames.contains(original)) {
                            resolved.put(original, pair.getSecond());
                        }
                    });
                INTERMEDIATE_MAPPINGS_CACHE.put(sourceNamespace, resolved);
                return resolved;
            }
        }
        return map;
    }
}
