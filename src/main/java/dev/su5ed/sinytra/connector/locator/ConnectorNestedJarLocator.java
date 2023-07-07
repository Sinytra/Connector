package dev.su5ed.sinytra.connector.locator;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import cpw.mods.jarhandling.SecureJar;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import dev.su5ed.sinytra.connector.loader.ConnectorLoaderModMetadata;
import dev.su5ed.sinytra.connector.transformer.JarTransformer;
import dev.su5ed.sinytra.connector.transformer.JarTransformer.FabricModPath;
import net.fabricmc.loader.impl.metadata.NestedJarEntry;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IDependencyLocator;
import net.minecraftforge.forgespi.locating.IModFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

public class ConnectorNestedJarLocator implements IDependencyLocator {
    private static final String NAME = "connector_locator_jij";

    @Override
    public List<IModFile> scanMods(Iterable<IModFile> loadedMods) {
        Path tempDir = ConnectorUtil.CONNECTOR_FOLDER.resolve("temp");

        Collection<String> loadedModIds = StreamSupport.stream(loadedMods.spliterator(), false)
            .flatMap(modFile -> Optional.ofNullable(modFile.getModFileInfo()).stream())
            .flatMap(modFileInfo -> modFileInfo.getMods().stream().map(IModInfo::getModId))
            .toList();

        List<JarTransformer.TransformableJar> discovered = StreamSupport.stream(loadedMods.spliterator(), false)
            .filter(modFile -> {
                IModFileInfo modFileInfo = modFile.getModFileInfo();
                return modFileInfo != null && modFileInfo.requiredLanguageLoaders().stream().anyMatch(l -> l.languageName().equals(ConnectorUtil.CONNECTOR_LANGUAGE));
            })
            .flatMap(modFile -> {
                SecureJar secureJar = modFile.getSecureJar();
                ConnectorLoaderModMetadata metadata = (ConnectorLoaderModMetadata) modFile.getModFileInfo().getFileProperties().get("metadata");
                return discoverNestedJarsRecursive(tempDir, secureJar, metadata.getJars(), loadedModIds);
            })
            .toList();
        List<JarTransformer.TransformableJar> uniquePaths = handleDuplicateMods(discovered);
        List<FabricModPath> transformed = JarTransformer.transform(uniquePaths);
        List<FabricModPath> moduleSafeJars = SplitPackageMerger.mergeSplitPackages(transformed);
        return moduleSafeJars.stream()
            .map(info -> ConnectorLocator.createConnectorModFile(info, this))
            .toList();
    }

    private static Stream<JarTransformer.TransformableJar> discoverNestedJarsRecursive(Path tempDir, SecureJar secureJar, Collection<NestedJarEntry> jars, Collection<String> loadedMods) {
        return jars.stream()
            .map(entry -> secureJar.getPath(entry.getFile()))
            .filter(Files::exists)
            .flatMap(path -> {
                JarTransformer.TransformableJar jar = uncheck(() -> prepareJijModFile(tempDir, secureJar.getPrimaryPath().getFileName().toString(), path));
                ConnectorLoaderModMetadata metadata = jar.modPath().metadata().modMetadata();
                return loadedMods.contains(metadata.getId()) ? Stream.empty()
                    : Stream.concat(Stream.of(jar), discoverNestedJarsRecursive(tempDir, SecureJar.from(jar.input().toPath()), metadata.getJars(), loadedMods));
            });
    }

    private static JarTransformer.TransformableJar prepareJijModFile(Path tempDir, String parentName, Path path) throws IOException {
        Files.createDirectories(tempDir);

        String parentNameWithoutExt = parentName.split("\\.(?!.*\\.)")[0];
        // Extract JiJ
        Path extracted = tempDir.resolve(parentNameWithoutExt + "$" + path.getFileName().toString());
        ConnectorUtil.cache("1", path, extracted, () -> Files.copy(path, extracted));

        return uncheck(() -> JarTransformer.cacheTransformableJar(extracted.toFile()));
    }

    private static List<JarTransformer.TransformableJar> handleDuplicateMods(List<JarTransformer.TransformableJar> mods) {
        Multimap<String, JarTransformer.TransformableJar> byId = HashMultimap.create();
        for (JarTransformer.TransformableJar jar : mods) {
            byId.put(jar.modPath().metadata().modMetadata().getId(), jar);
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
    public void scanFile(IModFile modFile, Consumer<Path> pathConsumer) {}

    @Override
    public void initArguments(Map<String, ?> arguments) {}

    @Override
    public boolean isValid(IModFile modFile) {
        return true;
    }
}
