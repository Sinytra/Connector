package dev.su5ed.connector.locator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import dev.su5ed.connector.ConnectorUtil;
import dev.su5ed.connector.loader.ConnectorLoaderModMetadata;
import dev.su5ed.connector.remap.AccessWidenerTransformer;
import dev.su5ed.connector.remap.MixinReplacementTransformer;
import dev.su5ed.connector.remap.PackMetadataGenerator;
import dev.su5ed.connector.remap.RefmapTransformer;
import dev.su5ed.connector.remap.SimpleRenamingTransformer;
import dev.su5ed.connector.remap.SrgRemappingReferenceMapper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.ParseMetadataException;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import net.minecraftforge.fart.api.Renamer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.ModDirTransformerDiscoverer;
import net.minecraftforge.fml.loading.StringUtils;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModProvider;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModJarMetadata;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import net.minecraftforge.forgespi.locating.IModProvider;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.INamedMappingFile;
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
import java.util.HashMap;
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

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;
import static dev.su5ed.connector.ConnectorUtil.uncheckThrowable;
import static net.minecraftforge.fml.loading.LogMarkers.SCAN;

public class ConnectorLocator extends AbstractJarFileModProvider implements IModLocator {
    private static final String NAME = "connector_locator";
    private static final String SUFFIX = ".jar";
    private static final String FABRIC_MAPPING_NAMESPACE = "Fabric-Mapping-Namespace";
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
            .filter(this::processConnectorPreviewJar)
            .map(path -> uncheck(() -> cacheRemapJar(path.toFile())))
            .toList();
        List<FabricModPath> moduleSafeJars = SplitPackageMerger.mergeSplitPackages(discoveredRemapped);
        Stream<IModLocator.ModFileOrException> fabricJars = moduleSafeJars.stream()
            .map(mod -> new ModFileOrException(createConnectorModFile(mod, this), null));
        Stream<IModLocator.ModFileOrException> additionalDeps = EmbeddedDependencies.locateAdditionalDependencies()
            .map(this::createMod);

        return Stream.concat(fabricJars, additionalDeps).toList();
    }

    protected boolean processConnectorPreviewJar(Path path) {
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
        Path path = modPath.path();
        ConnectorLoaderModMetadata metadata = modPath.metadata().modMetadata();
        ModJarMetadata mjm = uncheckThrowable(() -> (ModJarMetadata) MJM_INIT.invoke());
        SecureJar modJar = SecureJar.from(
            Manifest::new,
            jar -> jar.moduleDataProvider().findFile(ConnectorUtil.FABRIC_MOD_JSON).isPresent() ? mjm : JarMetadata.from(jar, path),
            (root, p) -> true,
            path
        );
        IModFile mod = new ModFile(modJar, provider, modFile -> ConnectorModMetadataParser.createForgeMetadata(modFile, metadata));
        mjm.setModFile(mod);
        return mod;
    }

    public static FabricModPath cacheRemapJar(File input) throws IOException {
        Path remappedDir = FMLPaths.MODSDIR.get().resolve("connector");
        Files.createDirectories(remappedDir);
        String suffix = "_mapped_official_1.19.4";

        String name = input.getName().split("\\.(?!.*\\.)")[0];
        Path output = remappedDir.resolve(name + suffix + ".jar");

        FabricModFileMetadata metadata = readModMetadata(input);
        ConnectorUtil.cache(String.valueOf(CACHE_VERSION), input.toPath(), output, () -> remapJar(input, remappedDir, output, metadata));

        return new FabricModPath(output, metadata);
    }

    private static FabricModFileMetadata readModMetadata(File input) throws IOException {
        ConnectorLoaderModMetadata metadata;
        Set<String> configs;
        Map<String, String> packageConfigs = new HashMap<>();
        Set<String> refmaps = new HashSet<>();
        Set<String> classes = new HashSet<>();
        Attributes manifestAttributes;
        try (JarFile jarFile = new JarFile(input)) {
            manifestAttributes = jarFile.getManifest().getMainAttributes();

            try (InputStream ins = jarFile.getInputStream(jarFile.getEntry(ConnectorUtil.FABRIC_MOD_JSON))) {
                LoaderModMetadata rawMetadata = ModMetadataParser.parseMetadata(ins, "", Collections.emptyList(), new VersionOverrides(), new DependencyOverrides(Paths.get("randomMissing")), false);
                metadata = new ConnectorLoaderModMetadata(rawMetadata);

                configs = new HashSet<>(metadata.getMixinConfigs(FabricLoader.getInstance().getEnvironmentType()));
            } catch (ParseMetadataException e) {
                throw new RuntimeException(e);
            }

            jarFile.stream()
                .filter(entry -> configs.contains(entry.getName()))
                .forEach(entry -> {
                    try (Reader reader = new InputStreamReader(jarFile.getInputStream(entry))) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        if (json.has("refmap")) {
                            String refmap = json.get("refmap").getAsString();
                            refmaps.add(refmap);
                        }
                        if (json.has("package")) {
                            String pkg = json.get("package").getAsString();
                            packageConfigs.put(pkg, entry.getName());

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
                });
        }
        return new FabricModFileMetadata(metadata, packageConfigs, refmaps, classes, manifestAttributes);
    }

    private static void remapJar(File input, Path remappedDir, Path output, FabricModFileMetadata metadata) throws IOException {
        Path mappingInput = EmbeddedDependencies.getContainedFile("mappings.tsrg");
        Path mappingsOutput = remappedDir.resolve(mappingInput.getFileName().toString());
        ConnectorUtil.cache(String.valueOf(CACHE_VERSION), mappingInput, mappingsOutput, () -> Files.copy(mappingInput, mappingsOutput));

        INamedMappingFile namedMapping = INamedMappingFile.load(mappingsOutput.toFile());
        // TODO Get current env mapping other than official
        String fromMapping = Optional.ofNullable(metadata.manifestAttributes.getValue(FABRIC_MAPPING_NAMESPACE)).filter(str -> !str.equals("named")).orElse("intermediary");
        IMappingFile modToOfficial = namedMapping.getMap(fromMapping, "official");
        IMappingFile intermediaryToSrg = namedMapping.getMap("intermediary", "srg");
        SrgRemappingReferenceMapper remapper = new SrgRemappingReferenceMapper(intermediaryToSrg);
        Collection<String> mixinConfigs = metadata.mixinConfigs.values();
        Map<String, String> mappings = modToOfficial.getClasses().stream()
            .flatMap(cls -> {
                Pair<String, String> clsRename = Pair.of(cls.getOriginal(), cls.getMapped());
                Stream<Pair<String, String>> fieldRenames = cls.getFields().stream().map(field -> Pair.of(field.getOriginal(), field.getMapped()));
                Stream<Pair<String, String>> methodRenames = cls.getMethods().stream().map(method -> Pair.of(method.getOriginal(), method.getMapped()));
                return Stream.concat(Stream.of(clsRename), Stream.concat(fieldRenames, methodRenames));
            })
            .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond, (a, b) -> a));

        try (Renamer renamer = Renamer.builder()
            .add(new SimpleRenamingTransformer(mappings))
            .add(new MixinReplacementTransformer(mixinConfigs, metadata.mixinClasses, mappings))
            .add(new RefmapTransformer(mixinConfigs, metadata.refmaps, remapper))
            .add(new AccessWidenerTransformer(metadata.modMetadata.getAccessWidener(), namedMapping))
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
     * @param mixinConfigs a map of mixin packages to their mixin config file name
     * @param refmaps      a map of mixin config name to its refmap name
     */
    public record FabricModFileMetadata(ConnectorLoaderModMetadata modMetadata, Map<String, String> mixinConfigs, Set<String> refmaps, Set<String> mixinClasses, Attributes manifestAttributes) {}

    public record FabricModPath(Path path, ConnectorLocator.FabricModFileMetadata metadata) {}
}
