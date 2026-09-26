package org.sinytra.connector.transformer.jar;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.CustomValue.CvType;
import net.fabricmc.loader.impl.metadata.*;
import org.sinytra.connector.transformer.TransformerEnvironment;
import org.sinytra.connector.transformer.transform.TransformerUtil;
import org.slf4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public class FabricJarReader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOOM_GENERATED_PROPERTY = "fabric-loom:generated";
    private static final String OVERRIDES_PROPERTY = "connector:overrides";
    private static final String LOOM_REMAP_ATTRIBUTE = "Fabric-Loom-Remap";

    public static FabricModFileMetadata readModMetadata(File input, TransformerEnvironment environment) throws IOException {
        try (JarFile jarFile = new JarFile(input)) {
            LoaderModMetadata metadata;
            Collection<String> selectedConfigs;
            Set<String> knownConfigs;
            Set<String> configs;
            boolean useExclusiveConfigs;
            try (InputStream ins = jarFile.getInputStream(jarFile.getEntry(TransformerUtil.FABRIC_MOD_JSON))) {
                VersionOverrides versionOverrides = environment.getVersionOverrides();
                DependencyOverrides dependencyOverrides = environment.getDependencyOverrides().get();
                LoaderModMetadata rawMetadata = ModMetadataParser.parseMetadata(
                    preProcessMetadata(ins),
                    "",
                    Collections.emptyList(),
                    versionOverrides,
                    dependencyOverrides,
                    false
                );
                metadata = environment.wrapModMetadata(rawMetadata);
                useExclusiveConfigs = hasMixinConfigOverrides(rawMetadata);

                Map<EnvType, Collection<String>> envMixinConfigs = Map.of(
                    EnvType.CLIENT, metadata.getMixinConfigs(EnvType.CLIENT),
                    EnvType.SERVER, metadata.getMixinConfigs(EnvType.SERVER)
                );

                knownConfigs = envMixinConfigs.values().stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toUnmodifiableSet());
                selectedConfigs = envMixinConfigs.get(environment.getEnvType());
                configs = new HashSet<>(selectedConfigs);
            } catch (ParseMetadataException e) {
                throw new RuntimeException(e);
            }
            boolean containsAT = jarFile.getEntry(TransformerUtil.AT_PATH) != null;

            Set<String> refmaps = new HashSet<>();
            Set<String> mixinClasses = new HashSet<>();
            Set<String> transformMixinClasses = useExclusiveConfigs ? new HashSet<>() : null;

            for (String configName : configs) {
                ZipEntry entry = jarFile.getEntry(configName);
                if (entry != null) {
                    Set<String> configMixinClasses = new HashSet<>();
                    readMixinConfigPackages(input, jarFile, entry, refmaps, configMixinClasses);

                    mixinClasses.addAll(configMixinClasses);
                    if (useExclusiveConfigs) {
                        transformMixinClasses.addAll(configMixinClasses);
                    }
                }
            }

            if (!useExclusiveConfigs) {
                // Find additional configs that may not be listed in mod metadata
                jarFile.stream()
                    .forEach(entry -> {
                        String name = entry.getName();
                        // Already discovered and ignored due to env setting
                        if (knownConfigs.contains(name)) {
                            return;
                        }
                        if ((name.endsWith(".mixins.json") || name.startsWith("mixins.") && name.endsWith(".json")) && configs.add(name)) {
                            readMixinConfigPackages(input, jarFile, entry, refmaps, mixinClasses);
                        }
                    });
            }

            Attributes manifestAttributes = Optional.ofNullable(jarFile.getManifest())
                .map(Manifest::getMainAttributes)
                .orElseGet(Attributes::new);
            boolean generated = isGeneratedLibraryJarMetadata(manifestAttributes, metadata);

            return new FabricModFileMetadata(
                metadata,
                selectedConfigs,
                configs,
                refmaps,
                mixinClasses,
                transformMixinClasses,
                manifestAttributes,
                containsAT,
                generated
            );
        }
    }

    private static void readMixinConfigPackages(File input, JarFile jarFile, ZipEntry entry, Set<? super String> refmaps, Set<? super String> mixinClasses) {
        try (Reader reader = new InputStreamReader(jarFile.getInputStream(entry))) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("refmap")) {
                String refmap = json.get("refmap").getAsString();
                refmaps.add(refmap);
            }
            if (json.has("package")) {
                String pkg = json.get("package").getAsString();
                for (String type : List.of("mixins", "client", "server")) {
                    if (json.has(type)) {
                        for (JsonElement mixin : json.getAsJsonArray(type)) {
                            if (mixin.isJsonPrimitive()) {
                                String className = pkg + "." + mixin.getAsString();
                                mixinClasses.add(className.replace('.', '/'));
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Error reading mixin config entry {} in file {}", entry.getName(), input.getAbsolutePath());
            throw new RuntimeException(t);
        }
    }

    private static boolean isGeneratedLibraryJarMetadata(Attributes manifestAttributes, LoaderModMetadata metadata) {
        CustomValue generatedValue = metadata.getCustomValue(LOOM_GENERATED_PROPERTY);
        if (generatedValue != null && generatedValue.getType() == CustomValue.CvType.BOOLEAN && generatedValue.getAsBoolean()) {
            String loomRemapAttribute = manifestAttributes.getValue(LOOM_REMAP_ATTRIBUTE);
            return loomRemapAttribute == null || !loomRemapAttribute.equals("true");
        }
        return false;
    }

    private static InputStream preProcessMetadata(InputStream ins) {
        JsonObject root = JsonParser.parseReader(new InputStreamReader(ins)).getAsJsonObject();

        JsonObject custom = root.getAsJsonObject("custom");
        if (custom != null) {
            JsonObject overrides = custom.getAsJsonObject(OVERRIDES_PROPERTY);
            if (overrides != null) {
                for (Entry<String, JsonElement> entry : overrides.entrySet()) {
                    root.add(entry.getKey(), entry.getValue());
                }
            }
        }

        String json = new Gson().toJson(root);
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean hasMixinConfigOverrides(LoaderModMetadata metadata) {
        return Optional.ofNullable(metadata.getCustomValue(OVERRIDES_PROPERTY))
            .map(val -> val.getType() == CvType.OBJECT && val.getAsObject().containsKey("mixins"))
            .orElse(false);
    }
}
