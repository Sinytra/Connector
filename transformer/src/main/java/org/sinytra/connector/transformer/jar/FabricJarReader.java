package org.sinytra.connector.transformer.jar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.impl.metadata.*;
import org.sinytra.connector.transformer.TransformerEnvironment;
import org.sinytra.connector.transformer.transform.TransformerUtil;
import org.slf4j.Logger;

import java.io.*;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public class FabricJarReader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOOM_GENERATED_PROPERTY = "fabric-loom:generated";
    private static final String LOOM_REMAP_ATTRIBUTE = "Fabric-Loom-Remap";

    public static FabricModFileMetadata readModMetadata(File input, TransformerEnvironment environment) throws IOException {
        try (JarFile jarFile = new JarFile(input)) {
            LoaderModMetadata metadata;
            Set<String> allConfigs;
            Set<String> configs;
            try (InputStream ins = jarFile.getInputStream(jarFile.getEntry(TransformerUtil.FABRIC_MOD_JSON))) {
                VersionOverrides versionOverrides = environment.getVersionOverrides();
                DependencyOverrides dependencyOverrides = environment.getDependencyOverrides().get();
                LoaderModMetadata rawMetadata = ModMetadataParser.parseMetadata(
                    ins,
                    "",
                    Collections.emptyList(),
                    versionOverrides,
                    dependencyOverrides,
                    false
                );
                metadata = environment.wrapModMetadata(rawMetadata);

                Map<EnvType, Collection<String>> envMixinConfigs = Map.of(
                    EnvType.CLIENT, metadata.getMixinConfigs(EnvType.CLIENT),
                    EnvType.SERVER, metadata.getMixinConfigs(EnvType.SERVER)
                );
                allConfigs = envMixinConfigs.values().stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());
                configs = new HashSet<>(envMixinConfigs.get(environment.getEnvType()));
            } catch (ParseMetadataException e) {
                throw new RuntimeException(e);
            }
            boolean containsAT = jarFile.getEntry(TransformerUtil.AT_PATH) != null;

            Set<String> refmaps = new HashSet<>();
            Set<String> mixinPackages = new HashSet<>();
            Set<String> mixinClasses = new HashSet<>();
            for (String configName : configs) {
                ZipEntry entry = jarFile.getEntry(configName);
                if (entry != null) {
                    readMixinConfigPackages(input, jarFile, entry, refmaps, mixinPackages, mixinClasses);
                }
            }

            // Find additional configs that may not be listed in mod metadata
            jarFile.stream()
                .forEach(entry -> {
                    String name = entry.getName();
                    // Already discovered and ignored due to env setting
                    if (allConfigs.contains(name)) {
                        return;
                    }
                    if ((name.endsWith(".mixins.json") || name.startsWith("mixins.") && name.endsWith(".json")) && configs.add(name)) {
                        readMixinConfigPackages(input, jarFile, entry, refmaps, mixinPackages, mixinClasses);
                    }
                });

            Attributes manifestAttributes = Optional.ofNullable(jarFile.getManifest())
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
                containsAT,
                generated
            );
        }
    }

    private static void readMixinConfigPackages(File input, JarFile jarFile, ZipEntry entry, Set<String> refmaps, Set<String> packages, Set<String> mixinClasses) {
        try (Reader reader = new InputStreamReader(jarFile.getInputStream(entry))) {
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

}
