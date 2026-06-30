package org.sinytra.connector.transformer.jar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.impl.metadata.*;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.jarcontents.JarResource;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.fml.util.PathPrettyPrinting;
import org.jetbrains.annotations.Nullable;
import org.sinytra.connector.transformer.TransformerEnvironment;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public class MetadataReader {
    public static final String FMJ = "fabric.mod.json";
    private static final String LOOM_GENERATED_PROPERTY = "fabric-loom:generated";
    private static final String LOOM_REMAP_ATTRIBUTE = "Fabric-Loom-Remap";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final VersionOverrides VERSION_OVERRIDES = new VersionOverrides();
    private static final DependencyOverrides DEP_OVERRIDES = new DependencyOverrides(Path.of("nonexistent"));

    @Nullable
    public static LoaderModMetadata readModMetadata(JarContents jar) {
        Path path = jar.getPrimaryPath();

        JarResource fmj = jar.get(FMJ);
        if (fmj == null) {
            LOGGER.warn(LogMarkers.LOADING, "Mod file {} is missing {} file", path, FMJ);
            return null;
        }

        try (InputStream ins = fmj.open()) {
            return ModMetadataParser.parseMetadata(
                ins,
                PathPrettyPrinting.prettyPrint(path),
                Collections.emptyList(),
                VERSION_OVERRIDES,
                DEP_OVERRIDES,
                false
            );
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read %s from %s".formatted(fmj, path), e);
        } catch (ParseMetadataException e) {
            throw new RuntimeException("Malformed %s from %s".formatted(fmj, path), e);
        }
    }

    public static FabricModFileMetadata readJarMetadata(JarContents jar, LoaderModMetadata metadata, TransformerEnvironment environment) {
        Map<EnvType, Collection<String>> envMixinConfigs = Map.of(
            EnvType.CLIENT, metadata.getMixinConfigs(EnvType.CLIENT),
            EnvType.SERVER, metadata.getMixinConfigs(EnvType.SERVER)
        );
        Set<String> allConfigs = envMixinConfigs.values().stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
        Set<String> configs = new HashSet<>(envMixinConfigs.get(environment.getEnvType()));

        Set<String> refmaps = new HashSet<>();
        Set<String> mixinPackages = new HashSet<>();
        Set<String> mixinClasses = new HashSet<>();

        jar.visitContent((name, resource) -> {
            // Read base config
            if (configs.contains(name)) {
                MixinConfigData data = readMixinConfig(resource);
                refmaps.addAll(data.refmaps());
                mixinPackages.addAll(data.packages());
                mixinClasses.addAll(data.classes());
            }

            // Already discovered and ignored due to env setting
            if (allConfigs.contains(name)) {
                return;
            }

            if ((name.endsWith(".mixins.json") || name.startsWith("mixins.") && name.endsWith(".json")) && configs.add(name)) {
                MixinConfigData data = readMixinConfig(resource);
                refmaps.addAll(data.refmaps());
                mixinPackages.addAll(data.packages());
                mixinClasses.addAll(data.classes());
            }
        });

        Attributes manifestAttributes = Optional.ofNullable(jar.getManifest())
            .map(Manifest::getMainAttributes)
            .orElseGet(Attributes::new);
        boolean generated = isGeneratedLibraryJarMetadata(manifestAttributes, metadata);

        return new FabricModFileMetadata(
            metadata,
            Set.copyOf(configs),
            configs,
            refmaps,
            mixinPackages,
            mixinClasses,
            manifestAttributes,
            generated
        );
    }

    private static boolean isGeneratedLibraryJarMetadata(Attributes manifestAttributes, LoaderModMetadata metadata) {
        CustomValue generatedValue = metadata.getCustomValue(LOOM_GENERATED_PROPERTY);
        if (generatedValue != null && generatedValue.getType() == CustomValue.CvType.BOOLEAN && generatedValue.getAsBoolean()) {
            String loomRemapAttribute = manifestAttributes.getValue(LOOM_REMAP_ATTRIBUTE);
            return loomRemapAttribute == null || !loomRemapAttribute.equals("true");
        }
        return false;
    }

    private static MixinConfigData readMixinConfig(JarResource file) {
        Set<String> refmaps = new HashSet<>();
        Set<String> packages = new HashSet<>();
        Set<String> classes = new HashSet<>();

        try (Reader reader = file.bufferedReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            if (json.has("refmap")) {
                String refmap = json.get("refmap").getAsString();
                refmaps.add(refmap);
            }

            if (json.has("package")) {
                String pkg = json.get("package").getAsString();
                if (!pkg.isEmpty()) {
                    String pkgPath = pkg.replace('.', '/') + '/';
                    packages.add(pkgPath);
                }

                for (String type : List.of("mixins", "client", "server")) {
                    if (json.has(type)) {
                        for (JsonElement mixin : json.getAsJsonArray(type)) {
                            if (mixin.isJsonPrimitive()) {
                                String className = pkg + "." + mixin.getAsString();
                                classes.add(className.replace('.', '/'));
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Error reading mixin config entry {}", file);
            throw new RuntimeException(t);
        }

        return new MixinConfigData(refmaps, packages, classes);
    }

    private record MixinConfigData(Set<String> refmaps, Set<String> packages, Set<String> classes) {
    }
}
