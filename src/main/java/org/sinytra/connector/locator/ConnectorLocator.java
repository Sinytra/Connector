package org.sinytra.connector.locator;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.jarmoduleinfo.JarModuleInfo;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.*;
import net.neoforged.neoforgespi.locating.IModFile.Type;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;
import org.sinytra.connector.ConnectorEarlyLoader;
import org.sinytra.connector.locator.ConnectorModFileReader.StubModFileInfo;
import org.sinytra.connector.locator.filter.SplitPackageMerger;
import org.sinytra.connector.locator.filter.SplitPackageMerger.FilteredPaths;
import org.sinytra.connector.locator.filter.SplitPackageMerger.SplitInputPath;
import org.sinytra.connector.locator.transform.ConnectorTransformerEnvironment;
import org.sinytra.connector.transformer.TransformerEnvironment;
import org.sinytra.connector.transformer.jar.FabricModFileMetadata;
import org.sinytra.connector.transformer.jar.JarTransformer;
import org.sinytra.connector.transformer.jar.JarTransformer.FabricModPath;
import org.sinytra.connector.transformer.jar.JarTransformer.TransformableJar;
import org.sinytra.connector.transformer.jar.JarTransformer.TransformedFabricModPath;
import org.sinytra.connector.transformer.jar.MetadataReader;
import org.sinytra.connector.transformer.transform.FabricMetadataTransformer;
import org.sinytra.launchpad.api.FabricModFactory;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.sinytra.connector.transformer.transform.TransformerUtil.uncheck;

public class ConnectorLocator implements IDependencyLocator {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public int getPriority() {
        return LOWEST_SYSTEM_PRIORITY;
    }

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        try {
            List<IModFile> loadedModsWithDeps = grabLocatedMods(pipeline);
            LocationResult results = locateFabricMods(loadedMods, loadedModsWithDeps);

            if (results != null) {
                results.mods().forEach(pipeline::addModFile);
                ConnectorEarlyLoader.init(results.mods(), results.output());

                // Create mod file for generated adapter mixins jar
                Path generatedAdapterJar = results.generatedJarPath();
                if (Files.exists(generatedAdapterJar)) {
                    pipeline.addPath(generatedAdapterJar, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
                }

                // Remove stubs
                loadedModsWithDeps.removeIf(m -> m.getModFileInfo() instanceof StubModFileInfo);
            }
        } catch (ModLoadingException e) {
            // Let these pass through
            throw e;
        } catch (Throwable t) {
            // Rethrow other exceptions
            StartupNotificationManager.addModMessage("CONNECTOR LOCATOR ERROR");
            LOGGER.error("Connector locator error", t);
            throw new ModLoadingException(ConnectorEarlyLoader.createGenericLoadingIssue(t, "Fabric mod discovery failed"));
        }
    }

    @Nullable
    private LocationResult locateFabricMods(List<IModFile> discoveredNeoMods, List<IModFile> discoveredAllMods) {
        String mcVersion = determineMcVersion(discoveredNeoMods);
        TransformerEnvironment environment = new ConnectorTransformerEnvironment(mcVersion, findCoremodsLibarary(discoveredAllMods));
        JarTransformer transformer = new JarTransformer(environment);

        // Get all existing mods
        Collection<SimpleModInfo> loadedModInfos = getPreviouslyDiscoveredMods(discoveredNeoMods);
        Collection<String> loadedModIds = loadedModInfos.stream()
            .filter(mod -> !mod.library())
            .map(SimpleModInfo::modid)
            .collect(Collectors.toUnmodifiableSet());
        Collection<IModFile> loadedModFiles = loadedModInfos.stream().map(SimpleModInfo::origin).toList();

        List<IModFile> interest = discoveredAllMods.stream()
            .filter(m -> m.getModFileInfo() instanceof StubModFileInfo)
            .filter(m -> shouldLoadMod(m, loadedModIds))
            .toList();
        List<TransformableJar> candidates = buildCandiates(interest, environment, transformer);

        // Collect mods that are (likely) going to be excluded by FML's UniqueModListBuilder. Exclude them from global split package filtering
        Collection<? super IModFile> ignoredModFiles = new ArrayList<>();

        // Remove mods loaded by FML
        List<TransformableJar> uniqueJars = handleDuplicateMods(candidates, loadedModInfos, ignoredModFiles);

        // Get renamer library classpath
        List<Path> renameLibs = loadedModFiles.stream()
            .map(IModFile::getFilePath)
            .toList();

        // Run jar transformations (or get existing outputs from cache)
        List<TransformedFabricModPath> transformed = transformer.transform(uniqueJars, renameLibs);

        List<TransformedFabricModPath> failing = transformed.stream()
            .filter(j -> j.auditTrail() != null && j.auditTrail().hasFailingMixins())
            .toList();
        MixinTransformSafeguard.prepare(failing);

        // Deal with split packages (thanks modules)
        List<SplitInputPath> splitInput = transformed.stream()
            .map(path -> {
                IModFile.Type type = determineModType(path.output(), discoveredAllMods);
                return new SplitInputPath(path.output().path(), path.output().metadata(), type);
            })
            .toList();
        List<FilteredPaths> moduleSafeJars = SplitPackageMerger.mergeSplitPackages(
            splitInput,
            loadedModFiles,
            ignoredModFiles
        );

        ModFileDiscoveryAttributes attributes = ModFileDiscoveryAttributes.DEFAULT.withDependencyLocator(this);
        List<IModFile> loadedMods = moduleSafeJars.stream()
            .map(out -> {
                JarContents contents = uncheck(() -> JarContents.ofFilteredPaths(out.paths()));
                IModFile mf = FabricModFactory.createModFile(contents, attributes, out.type());
                return Objects.requireNonNull(mf, "Invalid mod file");
            })
            .toList();

        return new LocationResult(loadedMods, transformed, environment.getGeneratedJarPath());
    }

    // Removes any duplicates from located connector mods, as well as mods that are already located by FML.
    private static List<TransformableJar> handleDuplicateMods(List<TransformableJar> rootMods, Collection<SimpleModInfo> loadedMods, Collection<? super IModFile> ignoredModFiles) {
        return rootMods.stream()
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
                    LOGGER.info(LogMarkers.SCAN, "Ignoring duplicate mod {} in file {}", id, jar.modPath().path().toAbsolutePath());
                    return false;
                }

                return true;
            })
            .toList();
    }

    private static List<TransformableJar> buildCandiates(List<IModFile> input, TransformerEnvironment environment, JarTransformer transformer) {
        Multimap<String, ComparableModFile> byId = HashMultimap.create();
        for (IModFile modFile : input) {
            LoaderModMetadata metadata = ((StubModFileInfo) modFile.getModFileInfo()).metadata();
            byId.put(metadata.getId(), new ComparableModFile(modFile, metadata.getVersion(), getNestingLevel(modFile)));
        }

        List<TransformableJar> output = new ArrayList<>();

        byId.asMap().forEach((_, files) -> {
            // Select by highest version and lowest nesting level
            IModFile latest = files.stream()
                .max(Comparator.comparing(ComparableModFile::version)
                    .thenComparing(ComparableModFile::nestingLevel, Comparator.reverseOrder()))
                .map(ComparableModFile::file)
                .orElseThrow();

            LoaderModMetadata loaderModMetadata = ((StubModFileInfo) latest.getModFileInfo()).metadata();
            FabricModFileMetadata metadata = MetadataReader.readJarMetadata(latest.getContents(), loaderModMetadata, environment);
            File file = latest.getFilePath().toFile();
            String name = getUniqueName(latest);
            TransformableJar txJar = uncheck(() -> transformer.cacheTransformableJar(file, metadata, name));
            output.add(txJar);
        });

        return output;
    }

    private static Collection<SimpleModInfo> getPreviouslyDiscoveredMods(List<IModFile> discoveredMods) {
        return discoveredMods.stream()
            .flatMap(modFile -> Optional.ofNullable(modFile.getModFileInfo()).stream())
            .flatMap(modFileInfo -> {
                if (modFileInfo instanceof StubModFileInfo) {
                    return Stream.empty();
                }

                IModFile modFile = modFileInfo.getFile();
                List<IModInfo> modInfos = modFileInfo.getMods();
                if (!modInfos.isEmpty()) {
                    return modInfos.stream()
                        .map(modInfo ->
                            new SimpleModInfo(modInfo.getModId(), modInfo.getVersion(), false, modFile));
                }

                JarContents contents = modFileInfo.getFile().getContents();
                JarModuleInfo info = JarModuleInfo.from(contents);

                String version = Optional.ofNullable(info.version()).orElse("0.0");
                return Stream.of(new SimpleModInfo(modFileInfo.getFile().getId(), new DefaultArtifactVersion(version), true, modFile));
            })
            .toList();
    }

    private static boolean shouldLoadMod(IModFile modFile, Collection<String> loadedNeoMods) {
        LoaderModMetadata metadata = ((StubModFileInfo) modFile.getModFileInfo()).metadata();

        EnvType env = FabricLoader.getInstance().getEnvironmentType();
        if (!metadata.loadsInEnvironment(env)) {
            LOGGER.debug("Not loading mod {} ({}) in current environment", metadata.getId(), modFile.getFilePath());
            return false;
        }

        // Skip loading mods that already have a native neo equivalent loaded
        String neoModId = FabricMetadataTransformer.normalizeModId(metadata.getId());
        return !loadedNeoMods.contains(neoModId);
    }

    private static IModFile.Type determineModType(FabricModPath path, List<IModFile> discoveredAllMods) {
        if (!path.metadata().generated()) {
            return Type.MOD;
        }

        try {
            JarContents jar = JarContents.ofPath(path.path());
            JarModuleInfo info = JarModuleInfo.from(jar);
            String id = info.name();

            boolean existing = discoveredAllMods.stream()
                .anyMatch(m -> id.equals(m.getId())
                    && m.getDiscoveryAttributes().parent() != null
                    && m.getDiscoveryAttributes().parent().getType() == Type.LIBRARY);
            if (existing) {
                return Type.LIBRARY;
            }
        } catch (IOException e) {
            LOGGER.error("Error determining mod type for {}", path.path(), e);
        }

        return Type.GAMELIBRARY;
    }

    @Nullable
    private static IModFile findCoremodsLibarary(List<IModFile> mods) {
        return mods.stream()
            .filter(m -> m.getId().equals("neoforge.coremods"))
            .findAny()
            .orElse(null);
    }

    private static int getNestingLevel(IModFile file) {
        int level = 0;
        for (IModFile f = file.getDiscoveryAttributes().parent(); f != null; f = f.getDiscoveryAttributes().parent()) {
            level++;
        }
        return level;
    }

    @SuppressWarnings("unchecked")
    private static List<IModFile> grabLocatedMods(IDiscoveryPipeline pipeline) throws Exception {
        Field field = pipeline.getClass().getDeclaredField("loadedFiles");
        field.setAccessible(true);
        return (List<IModFile>) field.get(pipeline);
    }

    private static String getUniqueName(IModFile file) {
        Deque<String> parents = new ArrayDeque<>();
        for (IModFile f = file; f != null; f = f.getDiscoveryAttributes().parent()) {
            String name = f.getFileName();
            int dot = name.lastIndexOf('.');
            parents.push(dot < 0 ? name : name.substring(0, dot));
        }
        return String.join("$", parents);
    }

    private static String determineMcVersion(Collection<IModFile> loadedMods) {
        String known = FMLLoader.getCurrent().getVersionInfo().mcVersion();
        if (known != null) {
            return known;
        }

        for (var modFile : loadedMods) {
            var mods = modFile.getModFileInfo().getMods();
            if (mods.isEmpty()) {
                continue;
            }
            var mainMod = mods.getFirst();
            if (modFile.getId().equals("minecraft")) {
                return mainMod.getVersion().toString();
            }
        }

        throw new RuntimeException("Unable to determine minecraft version");
    }

    private record SimpleModInfo(String modid, ArtifactVersion version, boolean library, @Nullable IModFile origin) {
    }

    private record LocationResult(List<IModFile> mods, List<TransformedFabricModPath> output, Path generatedJarPath) {
    }

    private record ComparableModFile(IModFile file, Version version, int nestingLevel) {
    }
}
