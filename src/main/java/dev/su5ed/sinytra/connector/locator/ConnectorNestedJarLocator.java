package dev.su5ed.sinytra.connector.locator;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import dev.su5ed.sinytra.connector.loader.ConnectorLoaderModMetadata;
import dev.su5ed.sinytra.connector.locator.ConnectorLocator.FabricModPath;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.NestedJarEntry;
import net.minecraftforge.forgespi.language.IModFileInfo;
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
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

public class ConnectorNestedJarLocator implements IDependencyLocator {
    private static final String NAME = "connector_locator_jij";

    @Override
    public List<IModFile> scanMods(Iterable<IModFile> loadedMods) {
        Path tempDir = ConnectorUtil.CONNECTOR_FOLDER.resolve("temp");

        List<FabricModPath> discoveredRemapped = StreamSupport.stream(loadedMods.spliterator(), false)
            .filter(modFile -> {
                IModFileInfo modFileInfo = modFile.getModFileInfo();
                return modFileInfo != null && modFileInfo.requiredLanguageLoaders().stream().anyMatch(l -> l.languageName().equals(ConnectorUtil.CONNECTOR_LANGUAGE));
            })
            .flatMap(modFile -> {
                SecureJar secureJar = modFile.getSecureJar();
                ConnectorLoaderModMetadata metadata = (ConnectorLoaderModMetadata) modFile.getModFileInfo().getFileProperties().get("metadata");
                return discoverNestedJarsRecursive(tempDir, secureJar, metadata.getJars());
            })
            .toList();
        List<FabricModPath> uniquePaths = handleDuplicateMods(discoveredRemapped);
        List<FabricModPath> moduleSafeJars = SplitPackageMerger.mergeSplitPackages(uniquePaths);
        return moduleSafeJars.stream()
            .map(info -> ConnectorLocator.createConnectorModFile(info, this))
            .toList();
    }

    private Stream<FabricModPath> discoverNestedJarsRecursive(Path tempDir, SecureJar secureJar, Collection<NestedJarEntry> jars) {
        return jars.stream()
            .map(entry -> secureJar.getPath(entry.getFile()))
            .filter(Files::exists)
            .flatMap(path -> {
                FabricModPath modInfo = uncheck(() -> prepareJijModFile(tempDir, secureJar.name(), path));
                return Stream.concat(Stream.of(modInfo), discoverNestedJarsRecursive(tempDir, SecureJar.from(modInfo.path()), modInfo.metadata().modMetadata().getJars()));
            });
    }

    // TODO Batch remap all nested jars
    private FabricModPath prepareJijModFile(Path tempDir, String parentName, Path path) throws IOException {
        Files.createDirectories(tempDir);

        String parentNameWithoutExt = parentName.split("\\.(?!.*\\.)")[0];
        // Extract JiJ
        Path extracted = tempDir.resolve(parentNameWithoutExt + "$" + path.getFileName().toString());
        ConnectorUtil.cache("1", path, extracted, () -> Files.copy(path, extracted));

        return LamdbaExceptionUtils.uncheck(() -> ConnectorLocator.cacheRemapJar(extracted.toFile()));
    }

    private List<FabricModPath> handleDuplicateMods(List<FabricModPath> mods) {
        Multimap<String, FabricModPath> map = HashMultimap.create();
        for (FabricModPath mod : mods) {
            LoaderModMetadata metadata = mod.metadata().modMetadata();
            map.put(metadata.getId(), mod);
        }
        List<FabricModPath> list = new ArrayList<>();
        map.asMap().forEach((modid, candidates) -> {
            FabricModPath mostRecent = candidates.stream()
                .max(Comparator.comparing(m -> m.metadata().modMetadata().getVersion()))
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
