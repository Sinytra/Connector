package org.sinytra.connector.locator;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarContentsBuilder;
import cpw.mods.jarhandling.SecureJar;
import net.fabricmc.loader.impl.metadata.NestedJarEntry;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.moddiscovery.ModJarMetadata;
import net.neoforged.fml.loading.moddiscovery.locators.JarInJarDependencyLocator;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;
import org.sinytra.connector.ConnectorEarlyLoader;
import org.sinytra.connector.locator.filter.ForgeModPackageFilter;
import org.sinytra.connector.locator.filter.SplitPackageMerger;
import org.sinytra.connector.transformer.jar.JarTransformer;
import org.sinytra.connector.util.ConnectorUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cpw.mods.modlauncher.api.LambdaExceptionUtils.rethrowFunction;
import static cpw.mods.modlauncher.api.LambdaExceptionUtils.uncheck;
import static net.neoforged.fml.loading.LogMarkers.SCAN;
import static org.sinytra.connector.transformer.jar.JarTransformer.cacheTransformableJar;

public class ConnectorLocator implements IDependencyLocator {
    public static final String PLACEHOLDER_PROPERTY = "connector:placeholder";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final List<Path> IGNORE_PATHS = new ArrayList<>();

    public static boolean shouldIgnorePath(Path path) {
        return IGNORE_PATHS.contains(path);
    }

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        if (ConnectorEarlyLoader.hasEncounteredException()) {
            LOGGER.error("Skipping mod scan due to previously encountered error");
            return;
        }
        try {
            DiscoveryResults results = locateFabricMods(loadedMods);
            if (results != null) {
                results.modFiles().forEach(pipeline::addModFile);

                // Create mod file for generated adapter mixins jar
                Path generatedAdapterJar = JarTransformer.getGeneratedJarPath();
                if (Files.exists(generatedAdapterJar)) {
                    pipeline.addPath(generatedAdapterJar, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
                }

                IGNORE_PATHS.addAll(results.originalPaths());
            }

            if (FMLEnvironment.production) {
                loadEmbeddedJars(pipeline);
            }
        } catch (ModLoadingException e) {
            // Let these pass through
            ConnectorEarlyLoader.addGenericLoadingException(e.getIssues());
        } catch (Throwable t) {
            // Rethrow other exceptions
            StartupNotificationManager.addModMessage("CONNECTOR LOCATOR ERROR");
            LOGGER.error("Connector locator error", t);
            ConnectorEarlyLoader.addGenericLoadingException(ConnectorEarlyLoader.createGenericLoadingIssue(t, "Fabric mod discovery failed"));
        } finally {
            // Handle forge mod split packages
            ForgeModPackageFilter.filterPackages(loadedMods);
        }
    }

    @Nullable
    private DiscoveryResults locateFabricMods(List<IModFile> discoveredMods) {
        LOGGER.debug(SCAN, "Scanning mods dir {} for mods", FMLPaths.MODSDIR.get());
        Path tempDir = ConnectorUtil.CONNECTOR_FOLDER.resolve("temp");

        // Get all existing mods
        Collection<SimpleModInfo> loadedModInfos = getPreviouslyDiscoveredMods(discoveredMods);
        Collection<IModFile> loadedModFiles = loadedModInfos.stream().map(SimpleModInfo::origin).toList();
        Collection<String> loadedModIds = loadedModInfos.stream().filter(mod -> !mod.library()).map(SimpleModInfo::modid).collect(Collectors.toUnmodifiableSet());

        // Discover fabric mod jars
        List<JarTransformer.TransformableJar> discoveredJars = FabricModsDiscoverer.scanFabricMods()
            .map(rethrowFunction(p -> cacheTransformableJar(p.toFile())))
            .filter(jar -> {
                ConnectorFabricModMetadata metadata = jar.modPath().metadata().modMetadata();
                return !shouldIgnoreMod(metadata, loadedModIds);
            })
            .toList();

        // Discover fabric nested mod jars
        Multimap<JarTransformer.TransformableJar, JarTransformer.TransformableJar> parentToChildren = HashMultimap.create();
        List<JarTransformer.TransformableJar> discoveredNestedJars = discoveredJars.stream()
            .flatMap(jar -> {
                ConnectorFabricModMetadata metadata = jar.modPath().metadata().modMetadata();
                return shouldIgnoreMod(metadata, loadedModIds) ? Stream.empty() : discoverNestedJarsRecursive(tempDir, jar, metadata.getJars(), parentToChildren, loadedModIds);
            })
            .toList();

        // Collect mods that are (likely) going to be excluded by FML's UniqueModListBuilder. Exclude them from global split package filtering
        Collection<? super IModFile> ignoredModFiles = new ArrayList<>();

        // Remove mods loaded by FML
        List<JarTransformer.TransformableJar> uniqueJars = handleDuplicateMods(discoveredJars, discoveredNestedJars, loadedModInfos, ignoredModFiles);

        // Ensure we have all required dependencies before transforming, remove side-only mods
        List<JarTransformer.TransformableJar> candidates = DependencyResolver.resolveDependencies(uniqueJars, parentToChildren, loadedModFiles);

        // Get renamer library classpath
        List<Path> renameLibs = loadedModFiles.stream().map(modFile -> modFile.getSecureJar().getRootPath()).toList();

        // Run jar transformations (or get existing outputs from cache)
        List<JarTransformer.TransformedFabricModPath> transformed = JarTransformer.transform(candidates, renameLibs, loadedModFiles);

        // Skip last step to save time if an error occured during transformation
        if (ConnectorEarlyLoader.hasEncounteredException()) {
            StartupNotificationManager.addModMessage("JAR TRANSFORMATION ERROR");
            LOGGER.error("Cancelling jar discovery due to previous error");
            return null;
        }

        // Deal with split packages (thanks modules)
        List<SplitPackageMerger.FilteredModPath> moduleSafeJars = SplitPackageMerger.mergeSplitPackages(transformed.stream().map(JarTransformer.TransformedFabricModPath::output).toList(), loadedModFiles, ignoredModFiles);

        List<IModFile> resultingFiles = moduleSafeJars.stream().map(ConnectorLocator::createConnectorModFile).toList();
        return new DiscoveryResults(resultingFiles, transformed.stream().map(JarTransformer.TransformedFabricModPath::input).toList());
    }

    private static IModFile createConnectorModFile(SplitPackageMerger.FilteredModPath modPath) {
        JarContents jarContents = new JarContentsBuilder().paths(modPath.paths()).pathFilter(modPath.filter()).build();
        ModJarMetadata modJarMetadata = new ModJarMetadata(jarContents);
        SecureJar secureJar = SecureJar.from(jarContents, modJarMetadata);
        IModFile modFile = IModFile.create(secureJar, f -> FabricModMetadataParser.createForgeMetadata(f, modPath.metadata().modMetadata(), modPath.metadata().visibleMixinConfigs(), modPath.metadata().generated()));
        modJarMetadata.setModFile(modFile);
        return modFile;
    }

    private static Stream<JarTransformer.TransformableJar> discoverNestedJarsRecursive(Path tempDir, JarTransformer.TransformableJar parent, Collection<NestedJarEntry> jars, Multimap<JarTransformer.TransformableJar, JarTransformer.TransformableJar> parentToChildren, Collection<String> loadedModIds) {
        SecureJar secureJar = SecureJar.from(parent.input().toPath());
        return jars.stream()
            .map(entry -> secureJar.getPath(entry.getFile()))
            .filter(Files::exists)
            .flatMap(path -> {
                JarTransformer.TransformableJar jar = uncheck(() -> prepareNestedJar(tempDir, secureJar.getPrimaryPath().getFileName().toString(), path));
                ConnectorFabricModMetadata metadata = jar.modPath().metadata().modMetadata();
                if (shouldIgnoreMod(metadata, loadedModIds)) {
                    return Stream.empty();
                }
                parentToChildren.put(parent, jar);
                return Stream.concat(Stream.of(jar), discoverNestedJarsRecursive(tempDir, jar, metadata.getJars(), parentToChildren, loadedModIds));
            });
    }

    private static JarTransformer.TransformableJar prepareNestedJar(Path tempDir, String parentName, Path path) throws IOException {
        Files.createDirectories(tempDir);

        String parentNameWithoutExt = parentName.split("\\.(?!.*\\.)")[0];
        // Extract JiJ
        Path extracted = tempDir.resolve(parentNameWithoutExt + "$" + path.getFileName().toString());
        ConnectorUtil.cache(path, extracted, () -> Files.copy(path, extracted));

        return uncheck(() -> JarTransformer.cacheTransformableJar(extracted.toFile()));
    }

    // Removes any duplicates from located connector mods, as well as mods that are already located by FML.
    private static List<JarTransformer.TransformableJar> handleDuplicateMods(List<JarTransformer.TransformableJar> rootMods, List<JarTransformer.TransformableJar> nestedMods, Collection<SimpleModInfo> loadedMods, Collection<? super IModFile> ignoredModFiles) {
        return Stream.concat(rootMods.stream(), nestedMods.stream())
            .filter(jar -> {
                String id = jar.modPath().metadata().modMetadata().getId();
                List<SimpleModInfo> forgeMods = loadedMods.stream()
                    .filter(mod -> mod.modid().equals(id))
                    .toList();
                // Add mods that are going to be excluded by FML's UniqueModListBuilder to the ignore list 
                if (forgeMods.stream().anyMatch(SimpleModInfo::library)) {
                    ArtifactVersion artifactVersion = new DefaultArtifactVersion(jar.modPath().metadata().modMetadata().getVersion().getFriendlyString());
                    SimpleModInfo fabricModInfo = new SimpleModInfo(id, artifactVersion, false, null);
                    // Sort mods by version, descending
                    List<SimpleModInfo> modsByVersion = Stream.concat(Stream.of(fabricModInfo), forgeMods.stream())
                        .sorted(Comparator.comparing(SimpleModInfo::version).reversed())
                        .toList();
                    // The fabric mod has the latest version - ignore others
                    if (modsByVersion.getFirst() == fabricModInfo) {
                        modsByVersion.subList(1, modsByVersion.size()).forEach(mod -> {
                            IModFile modFile = Objects.requireNonNull(mod.origin(), "Missing mod origin for mod " + mod.modid());
                            ignoredModFiles.add(modFile);
                        });
                        return true;
                    }
                }
                if (loadedMods.stream().anyMatch(mod -> mod.modid().equals(id))) {
                    LOGGER.info(SCAN, "Removing duplicate mod {} in file {}", id, jar.modPath().path().toAbsolutePath());
                    return false;
                }
                return true;
            })
            .toList();
    }

    private static boolean shouldIgnoreMod(ConnectorFabricModMetadata metadata, Collection<String> loadedModIds) {
        String id = metadata.getId();
        return ConnectorUtil.DISABLED_MODS.contains(id) || loadedModIds.contains(id);
    }

    private static Collection<SimpleModInfo> getPreviouslyDiscoveredMods(List<IModFile> discoveredMods) {
        return discoveredMods.stream()
            .flatMap(modFile -> Optional.ofNullable(modFile.getModFileInfo()).stream())
            .flatMap(modFileInfo -> {
                IModFile modFile = modFileInfo.getFile();
                List<IModInfo> modInfos = modFileInfo.getMods();
                // Ignore placeholder mods
                if (modFileInfo.getFileProperties().containsKey(PLACEHOLDER_PROPERTY)) {
                    // Set mod version to 0.0 to prioritize the Fabric mod when FML resolves duplicates
                    modInfos.forEach(mod -> mod.getVersion().parseVersion("0.0"));
                    return Stream.empty();
                }
                if (!modInfos.isEmpty()) {
                    return modInfos.stream().map(modInfo -> new SimpleModInfo(modInfo.getModId(), modInfo.getVersion(), false, modFile));
                }
                String version = modFileInfo.getFile().getSecureJar().moduleDataProvider().descriptor().version().map(ModuleDescriptor.Version::toString).orElse("0.0");
                return Stream.of(new SimpleModInfo(modFileInfo.moduleName(), new DefaultArtifactVersion(version), true, modFile));
            })
            .toList();
    }

    private static void loadEmbeddedJars(IDiscoveryPipeline pipeline) throws Exception {
        SecureJar secureJar = SecureJar.from(Path.of(ConnectorLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        IModFile modFile = IModFile.create(secureJar, JarModsDotTomlModFileReader::manifestParser);
        new JarInJarDependencyLocator().scanMods(List.of(modFile), pipeline);
    }

    private record SimpleModInfo(String modid, ArtifactVersion version, boolean library, @Nullable IModFile origin) {}

    private record DiscoveryResults(List<IModFile> modFiles, List<Path> originalPaths) {}
}
