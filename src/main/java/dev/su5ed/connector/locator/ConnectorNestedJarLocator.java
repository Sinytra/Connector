package dev.su5ed.connector.locator;

import cpw.mods.jarhandling.SecureJar;
import dev.su5ed.connector.ConnectorUtil;
import dev.su5ed.connector.loader.ConnectorLoaderModMetadata;
import dev.su5ed.connector.locator.ConnectorLocator.FabricModPath;
import net.fabricmc.loader.impl.metadata.NestedJarEntry;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.locating.IDependencyLocator;
import net.minecraftforge.forgespi.locating.IModFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
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
        Path tempDir = FMLPaths.MODSDIR.get().resolve("connector").resolve("temp");

        List<FabricModPath> discoveredRemapped = StreamSupport.stream(loadedMods.spliterator(), false)
            .filter(modFile -> {
                IModFileInfo modFileInfo = modFile.getModFileInfo();
                return modFileInfo != null && modFileInfo.requiredLanguageLoaders().stream().anyMatch(l -> l.languageName().equals("connector"));
            })
            .flatMap(modFile -> {
                SecureJar secureJar = modFile.getSecureJar();
                ConnectorLoaderModMetadata metadata = (ConnectorLoaderModMetadata) modFile.getModFileInfo().getFileProperties().get("metadata");
                return discoverNestedJarsRecursive(tempDir, secureJar, metadata.getJars());
            })
            .toList();
        List<FabricModPath> moduleSafeJars = SplitPackageMerger.mergeSplitPackages(discoveredRemapped);
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

        return uncheck(() -> ConnectorLocator.cacheRemapJar(extracted.toFile()));
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
