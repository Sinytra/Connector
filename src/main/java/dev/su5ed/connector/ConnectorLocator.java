package dev.su5ed.connector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import dev.su5ed.connector.fart.AccessWidenerTransformer;
import dev.su5ed.connector.fart.MixinTargetTransformer;
import dev.su5ed.connector.fart.RefmapTransformer;
import dev.su5ed.connector.fart.SrgRemappingReferenceMapper;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.ParseMetadataException;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fart.api.Renamer;
import net.minecraftforge.fart.api.Transformer;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LogMarkers;
import net.minecraftforge.fml.loading.ModDirTransformerDiscoverer;
import net.minecraftforge.fml.loading.StringUtils;
import net.minecraftforge.fml.loading.moddiscovery.AbstractModProvider;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModJarMetadata;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import net.minecraftforge.forgespi.locating.IModProvider;
import net.minecraftforge.forgespi.locating.ModFileLoadingException;
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
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

public class ConnectorLocator extends AbstractModProvider implements IModLocator {
    public static final String FABRIC_MOD_JSON = "fabric.mod.json";
    public static final String CONNECTOR_LANGUAGE = "connector";

    private static final String SUFFIX = ".jar";
    private static final String JIJ_ATTRIBUTE_PREFIX = "Additional-Dependencies-";
    private static final String LANGUAGE_JIJ_DEP = "Language";
    private static final String FABRIC_MAPPING_NAMESPACE = "Fabric-Mapping-Namespace";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Marker FART_MARKER = MarkerFactory.getMarker("FART");
    private static final int CACHE_VERSION = 1;

    private static final Path MOD_FOLDER = FMLPaths.MODSDIR.get();
    private static final Path SELF_PATH = uncheck(() -> {
        URL jarLocation = ConnectorLocator.class.getProtectionDomain().getCodeSource().getLocation();
        return Path.of(jarLocation.toURI());
    });
    private final Attributes attributes;

    public ConnectorLocator() {
        Manifest manifest = new Manifest();
        Path manifestPath = SELF_PATH.resolve("META-INF/MANIFEST.MF");
        uncheck(() -> manifest.read(Files.newInputStream(manifestPath)));
        this.attributes = manifest.getMainAttributes();

        FabricLoaderImpl.INSTANCE.setGameProvider(new ConnectorGameProvider());
    }

    @Override
    public List<IModLocator.ModFileOrException> scanMods() {
        LOGGER.debug(LogMarkers.SCAN, "Scanning mods dir {} for mods", MOD_FOLDER);
        List<Path> excluded = ModDirTransformerDiscoverer.allExcluded();

        Stream<IModLocator.ModFileOrException> fabricMods = uncheck(() -> Files.list(MOD_FOLDER))
            .filter(p -> !excluded.contains(p) && StringUtils.toLowerCase(p.getFileName().toString()).endsWith(SUFFIX))
            .sorted(Comparator.comparing(path -> StringUtils.toLowerCase(path.getFileName().toString())))
            .map(this::createConnectorMod);
        Stream<IModLocator.ModFileOrException> additionalDeps = getAdditionalDependencies()
            .map(this::createMod);

        return Stream.concat(fabricMods, additionalDeps).toList();
    }

    protected IModLocator.ModFileOrException createConnectorMod(Path path) {
        SecureJar secureJar = SecureJar.from(path);
        if (secureJar.moduleDataProvider().findFile(FABRIC_MOD_JSON).isPresent()) {
            LOGGER.debug(LogMarkers.SCAN, "Found {} mod: {}", FABRIC_MOD_JSON, path);

            IModFile modFile = createConnectorModFile(path, this);
            return new ModFileOrException(modFile, null);
        } else {
            return new IModLocator.ModFileOrException(null, new ModFileLoadingException("Invalid mod file found " + path));
        }
    }

    public static IModFile createConnectorModFile(Path path, IModProvider provider) {
        Path remapped = uncheck(() -> cacheRemapJar(path.toFile()));
        // TODO Method Handle
        ModJarMetadata mjm = uncheck(() -> {
            Constructor<ModJarMetadata> cst = ModJarMetadata.class.getDeclaredConstructor();
            cst.setAccessible(true);
            return cst.newInstance();
        });
        SecureJar modJar = SecureJar.from(
            Manifest::new,
            jar -> jar.moduleDataProvider().findFile(FABRIC_MOD_JSON).isPresent() ? mjm : JarMetadata.from(jar, remapped),
            (root, p) -> true,
            remapped
        );
        IModFile mod = new ModFile(modJar, provider, ConnectorModMetadataParser::fabricModJsonParser);
        mjm.setModFile(mod);
        return mod;
    }

    private static Path cacheRemapJar(File input) throws IOException {
        Path remappedDir = MOD_FOLDER.resolve("connector");
        Files.createDirectories(remappedDir);
        String suffix = "_mapped_official_1.19.4";

        String name = input.getName().split("\\.(?!.*\\.)")[0];
        Path output = remappedDir.resolve(name + suffix + ".jar");

        ConnectorUtil.cache(String.valueOf(CACHE_VERSION), input.toPath(), output, () -> remapJar(input, remappedDir, output));

        return output;
    }

    private static void remapJar(File input, Path remappedDir, Path output) throws IOException {
        Path mappingInput = SELF_PATH.resolve("mappings.tsrg");
        Path mappingsOutput = remappedDir.resolve(mappingInput.getFileName().toString());
        ConnectorUtil.cache(String.valueOf(CACHE_VERSION), mappingInput, mappingsOutput, () -> Files.copy(mappingInput, mappingsOutput));

        Set<String> configs;
        Set<String> refmaps = new HashSet<>();
        Set<String> mixinClasses = new HashSet<>();
        Attributes manifestAttributes;
        try (JarFile jarFile = new JarFile(input)) {
            manifestAttributes = jarFile.getManifest().getMainAttributes();

            try (InputStream ins = jarFile.getInputStream(jarFile.getEntry(FABRIC_MOD_JSON))) {
                // TODO Keep for later, pass into mod file info
                LoaderModMetadata metadata = ModMetadataParser.parseMetadata(ins, "", Collections.emptyList(), new VersionOverrides(), new DependencyOverrides(Paths.get("randomMissing")), false);

                configs = new HashSet<>(metadata.getMixinConfigs(getEnvType()));
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
                            String pkgPath = pkg.replace('.', '/') + '/';

                            Set.of("mixins", "client", "server").stream()
                                .flatMap(str -> {
                                    JsonArray array = json.getAsJsonArray(str);
                                    return Optional.ofNullable(array).stream()
                                        .flatMap(arr -> arr.asList().stream()
                                            .map(JsonElement::getAsString));
                                })
                                .map(name -> pkgPath + name.replace('.', '/'))
                                .forEach(mixinClasses::add);
                        }
                    } catch (IOException e) {
                        LOGGER.error("Error reading mixin config entry {} in file {}", entry.getName(), input.getAbsolutePath());
                        throw new UncheckedIOException(e);
                    }
                });
        }

        INamedMappingFile namedMapping = INamedMappingFile.load(mappingsOutput.toFile());
        // TODO Get current env mapping other than official
        String fromMapping = Optional.ofNullable(manifestAttributes.getValue(FABRIC_MAPPING_NAMESPACE)).filter(str -> !str.equals("named")).orElse("yarn");
        IMappingFile modToOfficial = namedMapping.getMap(fromMapping, "official");
        IMappingFile intermediaryToSrg = namedMapping.getMap("intermediary", "srg");
        SrgRemappingReferenceMapper remapper = new SrgRemappingReferenceMapper(intermediaryToSrg);

        try (Renamer renamer = Renamer.builder()
            .add(MixinTargetTransformer.factory(mixinClasses, modToOfficial))
            .add(Transformer.renamerFactory(modToOfficial, false))
            .add(new RefmapTransformer(configs, refmaps, remapper))
            .add(new AccessWidenerTransformer(namedMapping))
            .logger(s -> LOGGER.trace(FART_MARKER, s))
            .debug(s -> LOGGER.trace(FART_MARKER, s))
            .build()) {
            renamer.run(input, output.toFile());
        }
    }

    private Stream<Path> getAdditionalDependencies() {
        try {
            Path languageJij = getJarInJar(LANGUAGE_JIJ_DEP);
            return Stream.of(languageJij);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Path getJarInJar(String name) {
        String depName = this.attributes.getValue(JIJ_ATTRIBUTE_PREFIX + name);
        if (depName == null) {
            throw new IllegalArgumentException("Required " + name + " embedded jar not found");
        }
        return SELF_PATH.resolve(depName);
    }

    private static EnvType getEnvType() {
        return FMLEnvironment.dist == Dist.CLIENT ? EnvType.CLIENT : EnvType.SERVER;
    }

    @Override
    public String name() {
        return "connector";
    }

    @Override
    public void scanFile(IModFile modFile, Consumer<Path> pathConsumer) {}

    @Override
    public void initArguments(Map<String, ?> arguments) {}
}
