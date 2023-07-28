package dev.su5ed.sinytra.connector.locator;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import dev.su5ed.sinytra.connector.loader.ConnectorLoaderModMetadata;
import dev.su5ed.sinytra.connector.transformer.JarTransformer;
import net.fabricmc.loader.impl.metadata.NestedJarEntry;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.ModDirTransformerDiscoverer;
import net.minecraftforge.fml.loading.StringUtils;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModProvider;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModJarMetadata;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IDependencyLocator;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModProvider;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.rethrowFunction;
import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;
import static dev.su5ed.sinytra.connector.transformer.JarTransformer.cacheTransformableJar;
import static net.minecraftforge.fml.loading.LogMarkers.SCAN;

public class ConnectorLocator extends AbstractJarFileModProvider implements IDependencyLocator {
    private static final String NAME = "connector_locator";
    private static final String SUFFIX = ".jar";
    private static final Set<String> DISABLED_MODS = Set.of("fabric_api");

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final MethodHandle MJM_INIT = uncheck(() -> MethodHandles.privateLookupIn(ModJarMetadata.class, MethodHandles.lookup()).findConstructor(ModJarMetadata.class, MethodType.methodType(void.class)));

    @Override
    public List<IModFile> scanMods(Iterable<IModFile> loadedMods) {
        LOGGER.debug(SCAN, "Scanning mods dir {} for mods", FMLPaths.MODSDIR.get());
        List<Path> excluded = ModDirTransformerDiscoverer.allExcluded();
        Path tempDir = ConnectorUtil.CONNECTOR_FOLDER.resolve("temp");
        // Get all existing mod ids
        Collection<String> loadedModIds = StreamSupport.stream(loadedMods.spliterator(), false)
            .flatMap(modFile -> Optional.ofNullable(modFile.getModFileInfo()).stream())
            .flatMap(modFileInfo -> modFileInfo.getMods().stream().map(IModInfo::getModId))
            .toList();
        // Discover fabric mod jars
        List<JarTransformer.TransformableJar> discoveredJars = uncheck(() -> Files.list(FMLPaths.MODSDIR.get()))
            .filter(p -> !excluded.contains(p) && StringUtils.toLowerCase(p.getFileName().toString()).endsWith(SUFFIX))
            .sorted(Comparator.comparing(path -> StringUtils.toLowerCase(path.getFileName().toString())))
            .filter(this::locateFabricModJar)
            .map(rethrowFunction(p -> cacheTransformableJar(p.toFile())))
            // No matter what, we remove upstream fabric api from loading to prevent it from conflicting with FFAPI 
            // The unique mod filter isn't enough to handle api modules that have been left behind and not ported
            // I'm sorry for hardcoding this, but it seems to be the best way around
            .filter(jar -> !DISABLED_MODS.contains(jar.modPath().metadata().modMetadata().getId()))
            .toList();
        // Discover fabric nested mod jars
        List<JarTransformer.TransformableJar> discoveredNestedJars = discoveredJars.stream()
            .flatMap(jar -> {
                SecureJar secureJar = SecureJar.from(jar.input().toPath());
                ConnectorLoaderModMetadata metadata = jar.modPath().metadata().modMetadata();
                return discoverNestedJarsRecursive(tempDir, secureJar, metadata.getJars());
            })
            .toList();
        // Get renamer library classpath
        List<Path> renameLibs = StreamSupport.stream(loadedMods.spliterator(), false).map(modFile -> modFile.getSecureJar().getRootPath()).toList();
        // Remove duplicates and existing mods
        List<JarTransformer.TransformableJar> uniqueNestedJars = handleDuplicateMods(Objects.requireNonNull(discoveredNestedJars), loadedModIds);
        // Merge outer and nested jar lists
        List<JarTransformer.TransformableJar> allJars = Stream.concat(discoveredJars.stream(), uniqueNestedJars.stream()).toList();
        // Run jar transformations (or get existing outputs from cache)
        List<JarTransformer.FabricModPath> transformed = JarTransformer.transform(allJars, renameLibs);
        // Deal with split packages (thanks modules)
        List<SplitPackageMerger.FilteredModPath> moduleSafeJars = SplitPackageMerger.mergeSplitPackages(transformed, loadedMods);
        Stream<IModFile> fabricJars;
        // Handle jar transformation errors
        if (ConnectorEarlyLoader.getLoadingException() != null) {
            StartupNotificationManager.addModMessage("JAR TRANSFORMATION ERROR");
            LOGGER.error("Cancelling Connector jar discovery due to previous error", ConnectorEarlyLoader.getLoadingException());
            fabricJars = Stream.empty();
        }
        else {
            fabricJars = moduleSafeJars.stream()
                .map(mod -> createConnectorModFile(mod, this));
        }
        Stream<IModFile> embeddedDeps = EmbeddedDependencies.locateEmbeddedJars()
            .map(path -> createMod(path).file())
            .filter(Objects::nonNull);

        return Stream.concat(fabricJars, embeddedDeps).toList();
    }

    public static IModFile createConnectorModFile(SplitPackageMerger.FilteredModPath modPath, IModProvider provider) {
        ModJarMetadata mjm = ConnectorUtil.uncheckThrowable(() -> (ModJarMetadata) MJM_INIT.invoke());
        SecureJar modJar = SecureJar.from(Manifest::new, jar -> mjm, modPath.filter(), modPath.paths());
        IModFile mod = new ModFile(modJar, provider, modFile -> ConnectorModMetadataParser.createForgeMetadata(modFile, modPath.metadata().modMetadata()));
        mjm.setModFile(mod);
        return mod;
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

    private static Stream<JarTransformer.TransformableJar> discoverNestedJarsRecursive(Path tempDir, SecureJar secureJar, Collection<NestedJarEntry> jars) {
        return jars.stream()
            .map(entry -> secureJar.getPath(entry.getFile()))
            .filter(Files::exists)
            .flatMap(path -> {
                JarTransformer.TransformableJar jar = uncheck(() -> prepareNestedJar(tempDir, secureJar.getPrimaryPath().getFileName().toString(), path));
                ConnectorLoaderModMetadata metadata = jar.modPath().metadata().modMetadata();
                return Stream.concat(Stream.of(jar), discoverNestedJarsRecursive(tempDir, SecureJar.from(jar.input().toPath()), metadata.getJars()));
            });
    }

    private static JarTransformer.TransformableJar prepareNestedJar(Path tempDir, String parentName, Path path) throws IOException {
        Files.createDirectories(tempDir);

        String parentNameWithoutExt = parentName.split("\\.(?!.*\\.)")[0];
        // Extract JiJ
        Path extracted = tempDir.resolve(parentNameWithoutExt + "$" + path.getFileName().toString());
        ConnectorUtil.cache("1", path, extracted, () -> Files.copy(path, extracted));

        return uncheck(() -> JarTransformer.cacheTransformableJar(extracted.toFile()));
    }

    // Removes any duplicates from located connector mods, as well as mods that are already located by FML.
    private static List<JarTransformer.TransformableJar> handleDuplicateMods(List<JarTransformer.TransformableJar> mods, Collection<String> loadedModIds) {
        Multimap<String, JarTransformer.TransformableJar> byId = HashMultimap.create();
        for (JarTransformer.TransformableJar jar : mods) {
            String id = jar.modPath().metadata().modMetadata().getId();
            if (!loadedModIds.contains(id)) {
                byId.put(id, jar);
            }
            else {
                LOGGER.info(SCAN, "Removing duplicate mod {} from file {}", id, jar.modPath().path().toAbsolutePath());
            }
        }
        List<JarTransformer.TransformableJar> list = new ArrayList<>();
        byId.asMap().forEach((modid, candidates) -> {
            JarTransformer.TransformableJar mostRecent = candidates.stream()
                .max(Comparator.comparing(m -> m.modPath().metadata().modMetadata().getVersion()))
                .orElseThrow();
            list.add(mostRecent);
        });
        return list;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {}
}
