package dev.su5ed.sinytra.connector.locator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import dev.su5ed.sinytra.connector.loader.ConnectorLoaderModMetadata;
import dev.su5ed.sinytra.connector.transformer.AccessWidenerTransformer;
import dev.su5ed.sinytra.connector.transformer.MixinReplacementTransformer;
import dev.su5ed.sinytra.connector.transformer.PackMetadataGenerator;
import dev.su5ed.sinytra.connector.transformer.RefmapTransformer;
import dev.su5ed.sinytra.connector.transformer.SimpleRenamingTransformer;
import dev.su5ed.sinytra.connector.transformer.SrgRemappingReferenceMapper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.MappingResolverImpl;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.ParseMetadataException;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import net.minecraftforge.fart.api.Renamer;
import net.minecraftforge.fml.loading.*;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModProvider;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModJarMetadata;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import net.minecraftforge.forgespi.locating.IModProvider;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;
import static net.minecraftforge.fml.loading.LogMarkers.SCAN;

public class ConnectorLocator extends AbstractJarFileModProvider implements IModLocator {
    private static final String NAME = "connector_locator";
    private static final String SUFFIX = ".jar";
    private static final String FABRIC_MAPPING_NAMESPACE = "Fabric-Mapping-Namespace";
    private static final String MAPPED_SUFFIX = "_mapped_official_" + FMLLoader.versionInfo().mcVersion();
    // Increment to invalidate cache
    private static final int CACHE_VERSION = 1;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Marker REMAP_MARKER = MarkerFactory.getMarker("REMAP");
    private static final MethodHandle MJM_INIT = uncheck(() -> MethodHandles.privateLookupIn(ModJarMetadata.class, MethodHandles.lookup()).findConstructor(ModJarMetadata.class, MethodType.methodType(void.class)));

    @Override
    public List<IModLocator.ModFileOrException> scanMods() {
        LOGGER.debug(SCAN, "Scanning mods dir {} for mods", FMLPaths.MODSDIR.get());
        List<Path> excluded = ModDirTransformerDiscoverer.allExcluded();

        List<FabricModPath> discoveredRemapped = uncheck(() -> Files.list(FMLPaths.MODSDIR.get()))
            .filter(p -> !excluded.contains(p) && StringUtils.toLowerCase(p.getFileName().toString()).endsWith(SUFFIX))
            .sorted(Comparator.comparing(path -> StringUtils.toLowerCase(path.getFileName().toString())))
            .filter(this::locateFabricModJar)
            .map(path -> uncheck(() -> cacheRemapJar(path.toFile())))
            .toList();
        List<FabricModPath> moduleSafeJars = SplitPackageMerger.mergeSplitPackages(discoveredRemapped);
        Stream<IModLocator.ModFileOrException> fabricJars = moduleSafeJars.stream()
            .map(mod -> new ModFileOrException(createConnectorModFile(mod, this), null));
        Stream<IModLocator.ModFileOrException> embeddedDeps = EmbeddedDependencies.locateAdditionalDependencies()
            .map(this::createMod);

        return Stream.concat(fabricJars, embeddedDeps).toList();
    }

    protected boolean locateFabricModJar(Path path) {
        SecureJar secureJar = SecureJar.from(path);
        String name = secureJar.name();
        if (secureJar.moduleDataProvider().findFile(ConnectorUtil.FABRIC_MOD_JSON).isPresent()) {
            LOGGER.debug(SCAN, "Found {} mod: {}", ConnectorUtil.FABRIC_MOD_JSON, path);
            return true;
        }
        LOGGER.info(SCAN, "Fabric mod metadata not found in jar {}, ignoring", name);
        return false;
    }

    public static IModFile createConnectorModFile(FabricModPath modPath, IModProvider provider) {
        ModJarMetadata mjm = ConnectorUtil.uncheckThrowable(() -> (ModJarMetadata) MJM_INIT.invoke());
        SecureJar modJar = SecureJar.from(Manifest::new, jar -> mjm, modPath.path());
        IModFile mod = new ModFile(modJar, provider, modFile -> ConnectorModMetadataParser.createForgeMetadata(modFile, modPath.metadata().modMetadata()));
        mjm.setModFile(mod);
        return mod;
    }

    public static FabricModPath cacheRemapJar(File input) throws IOException {
        Files.createDirectories(ConnectorUtil.CONNECTOR_FOLDER);
        String name = input.getName().split("\\.(?!.*\\.)")[0];
        Path output = ConnectorUtil.CONNECTOR_FOLDER.resolve(name + MAPPED_SUFFIX + ".jar");

        FabricModFileMetadata metadata = readModMetadata(input);
        ConnectorUtil.cache(String.valueOf(CACHE_VERSION), input.toPath(), output, () -> transformJar(input, output, metadata));

        return new FabricModPath(output, metadata);
    }

    private static FabricModFileMetadata readModMetadata(File input) throws IOException {
        try (JarFile jarFile = new JarFile(input)) {
            Attributes manifestAttributes = jarFile.getManifest().getMainAttributes();

            ConnectorLoaderModMetadata metadata;
            Set<String> configs;
            try (InputStream ins = jarFile.getInputStream(jarFile.getEntry(ConnectorUtil.FABRIC_MOD_JSON))) {
                LoaderModMetadata rawMetadata = ModMetadataParser.parseMetadata(ins, "", Collections.emptyList(), new VersionOverrides(), new DependencyOverrides(Paths.get("randomMissing")), false);
                metadata = new ConnectorLoaderModMetadata(rawMetadata);

                configs = new HashSet<>(metadata.getMixinConfigs(FabricLoader.getInstance().getEnvironmentType()));
            } catch (ParseMetadataException e) {
                throw new RuntimeException(e);
            }

            Set<String> refmaps = new HashSet<>();
            Set<String> classes = new HashSet<>();
            for (String configName : configs) {
                ZipEntry entry = jarFile.getEntry(configName);
                if (entry != null) {
                    try (Reader reader = new InputStreamReader(jarFile.getInputStream(entry))) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        if (json.has("refmap")) {
                            String refmap = json.get("refmap").getAsString();
                            refmaps.add(refmap);
                        }
                        if (json.has("package")) {
                            String pkg = json.get("package").getAsString();
                            String pkgPath = pkg.replace('.', '/') + '/';
                            Set.of("mixins", "client", "server").stream()
                                .flatMap(str -> {
                                    JsonArray array = json.getAsJsonArray(str);
                                    return Optional.ofNullable(array).stream()
                                        .flatMap(arr -> arr.asList().stream()
                                            .map(JsonElement::getAsString));
                                })
                                .map(side -> pkgPath + side.replace('.', '/'))
                                .forEach(classes::add);
                        }
                    } catch (IOException e) {
                        LOGGER.error("Error reading mixin config entry {} in file {}", entry.getName(), input.getAbsolutePath());
                        throw new UncheckedIOException(e);
                    }
                }
            }
            return new FabricModFileMetadata(metadata, configs, refmaps, classes, manifestAttributes);
        }
    }

    private static void transformJar(File input, Path output, FabricModFileMetadata metadata) throws IOException {
        MappingResolverImpl resolver = FabricLoaderImpl.INSTANCE.getMappingResolver();
        SrgRemappingReferenceMapper remapper = new SrgRemappingReferenceMapper(resolver.getMap("intermediary", FMLEnvironment.naming));
        String fromMapping = Optional.ofNullable(metadata.manifestAttributes.getValue(FABRIC_MAPPING_NAMESPACE)).orElse("intermediary");
        Map<String, String> mappings = resolver.getCurrentMap(fromMapping).getClasses().stream()
            .flatMap(cls -> {
                Pair<String, String> clsRename = Pair.of(cls.getOriginal(), cls.getMapped());
                Stream<Pair<String, String>> fieldRenames = cls.getFields().stream().map(field -> Pair.of(field.getOriginal(), field.getMapped()));
                Stream<Pair<String, String>> methodRenames = cls.getMethods().stream().map(method -> Pair.of(method.getOriginal(), method.getMapped()));
                return Stream.concat(Stream.of(clsRename), Stream.concat(fieldRenames, methodRenames));
            })
            .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond, (a, b) -> a));

        try (Renamer renamer = Renamer.builder()
            .add(new SimpleRenamingTransformer(mappings))
            .add(new MixinReplacementTransformer(metadata.mixinClasses, mappings))
            .add(new RefmapTransformer(metadata.mixinConfigs, metadata.refmaps, remapper))
            .add(new AccessWidenerTransformer(metadata.modMetadata.getAccessWidener(), resolver))
            .add(new PackMetadataGenerator(metadata.modMetadata.getId()))
            .logger(s -> LOGGER.trace(REMAP_MARKER, s))
            .debug(s -> LOGGER.trace(REMAP_MARKER, s))
            .build()) {
            renamer.run(input, output.toFile());
        }
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {}

    /**
     * @param refmaps a map of mixin config name to its refmap name
     */
    public record FabricModFileMetadata(ConnectorLoaderModMetadata modMetadata, Collection<String> mixinConfigs, Set<String> refmaps, Set<String> mixinClasses, Attributes manifestAttributes) {}

    public record FabricModPath(Path path, ConnectorLocator.FabricModFileMetadata metadata) {}
}
