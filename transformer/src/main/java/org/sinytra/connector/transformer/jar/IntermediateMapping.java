package org.sinytra.connector.transformer.jar;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.MappingResolverImpl;
import net.minecraftforge.srgutils.IMappingFile;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Stream;

public class IntermediateMapping {
    private static final Map<String, IntermediateMapping> INTERMEDIATE_MAPPINGS_CACHE = new HashMap<>();
    // Filter out non-obfuscated method names used in mapping namespaces as those don't need
    // to be remapped and will only cause issues with our barebones find/replace remapper
    private static final Map<String, Collection<String>> MAPPING_PREFIXES = Map.of(
        "intermediary", Set.of("net/minecraft/class_", "field_", "method_", "comp_")
    );
    private static final Logger LOGGER = LogUtils.getLogger();

    // Original -> Mapped
    private final Map<String, String> mappings;
    // Original + Descriptor -> Mapped
    private final Map<String, String> extendedMappings;
    // Original -> Mapped name + original descriptor, when the original method name is unambiguous
    private final Map<String, MethodMapping> methodMappings;

    public static IntermediateMapping get(String sourceNamespace) {
        IntermediateMapping existing = INTERMEDIATE_MAPPINGS_CACHE.get(sourceNamespace);
        if (existing == null) {
            synchronized (JarTransformer.class) {
                existing = INTERMEDIATE_MAPPINGS_CACHE.get(sourceNamespace);
                if (existing != null) {
                    return existing;
                }

                LOGGER.debug(JarTransformer.TRANSFORM_MARKER, "Creating flat intermediate mapping for namespace {}", sourceNamespace);
                // Intermediary sometimes contains duplicate names for different methods (why?). We exclude those.
                Collection<String> prefixes = MAPPING_PREFIXES.get(sourceNamespace);
                MappingResolverImpl resolver = FabricLoaderImpl.INSTANCE.getMappingResolver();
                Map<String, String> resolved = new HashMap<>();
                Map<String, IMappingFile.INode> buffer = new HashMap<>();
                Map<String, String> extendedMappings = new HashMap<>();
                Map<String, MethodMapping> methodMappings = new HashMap<>();
                Set<String> ambiguousMethods = new HashSet<>();
                resolver.getCurrentMap(sourceNamespace).getClasses().stream()
                    .flatMap(cls -> Stream.concat(Stream.of(cls), Stream.concat(cls.getFields().stream(), cls.getMethods().stream()))
                        .filter(node -> prefixes.stream().anyMatch(node.getOriginal()::startsWith)))
                    .forEach(node -> {
                        String original = node.getOriginal();
                        String mapped = node.getMapped();
                        if (node instanceof IMappingFile.IMethod method && method.getDescriptor() != null) {
                            MethodMapping methodMapping = new MethodMapping(mapped, method.getDescriptor());
                            MethodMapping existingMethod = methodMappings.get(original);
                            if (existingMethod == null && !ambiguousMethods.contains(original)) {
                                methodMappings.put(original, methodMapping);
                            }
                            else if (!methodMapping.equals(existingMethod)) {
                                methodMappings.remove(original);
                                ambiguousMethods.add(original);
                            }
                        }
                        String mapping = resolved.get(original);
                        if (mapping != null && !mapping.equals(mapped)) {
                            resolved.remove(original);
                            extendedMappings.put(getMappingKey(buffer.remove(original)), mapping);
                            extendedMappings.put(getMappingKey(node), mapped);
                        }
                        else if (!extendedMappings.containsKey(getMappingKey(node))) {
                            resolved.put(original, mapped);
                            buffer.put(original, node);
                        }
                    });
                IntermediateMapping mapping = new IntermediateMapping(resolved, extendedMappings, methodMappings);
                INTERMEDIATE_MAPPINGS_CACHE.put(sourceNamespace, mapping);
                return mapping;
            }
        }
        return existing;
    }

    private static String getMappingKey(IMappingFile.INode node) {
        if (node instanceof IMappingFile.IField field) {
            String desc = field.getDescriptor();
            return field.getOriginal() + (desc != null ? ":" + desc : "");
        }
        else if (node instanceof IMappingFile.IMethod method) {
            return method.getOriginal() + Objects.requireNonNullElse(method.getDescriptor(), "");
        }
        return node.getOriginal();
    }

    public IntermediateMapping(Map<String, String> mappings, Map<String, String> extendedMappings, Map<String, MethodMapping> methodMappings) {
        this.mappings = mappings;
        this.extendedMappings = extendedMappings;
        this.methodMappings = methodMappings;
    }

    @Nullable
    public String map(String name) {
        return this.mappings.get(name);
    }

    @Nullable
    public String mapField(String name, @Nullable String desc) {
        String mapped = this.mappings.get(name);
        if (mapped == null) {
            String qualifier = name + ":" + desc;
            return this.extendedMappings.get(qualifier);
        }
        return mapped;
    }

    public String mapMethodOrDefault(String name, String desc) {
        String mapped = mapMethod(name, desc);
        return mapped != null ? mapped : name;
    }

    @Nullable
    public MethodMapping getMethodMapping(String name) {
        return this.methodMappings.get(name);
    }

    @Nullable
    public String mapMethod(String name, String desc) {
        String mapped = this.mappings.get(name);
        if (mapped == null) {
            String qualifier = name + desc;
            return this.extendedMappings.get(qualifier);
        }
        return mapped;
    }

    public record MethodMapping(String mappedName, String descriptor) {
    }
}
