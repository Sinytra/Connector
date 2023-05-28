package dev.su5ed.connector;

import cpw.mods.jarhandling.SecureJar;
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
import java.util.stream.StreamSupport;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

public class ConnectorNestedJarLocator implements IDependencyLocator {
    @SuppressWarnings("unchecked")
    @Override
    public List<IModFile> scanMods(Iterable<IModFile> loadedMods) {
        Path tempDir = FMLPaths.MODSDIR.get().resolve("connector").resolve("temp");

        List<Path> discoveredRemapped = StreamSupport.stream(loadedMods.spliterator(), false)
            .filter(modFile -> {
                IModFileInfo modFileInfo = modFile.getModFileInfo();
                return modFileInfo != null && modFileInfo.requiredLanguageLoaders().stream().anyMatch(l -> l.languageName().equals("connector"));
            })
            .flatMap(modFile -> {
                SecureJar secureJar = modFile.getSecureJar();
                Collection<NestedJarEntry> jars = (Collection<NestedJarEntry>) modFile.getModFileInfo().getFileProperties().get("jars");
                return jars.stream()
                    .map(entry -> secureJar.getPath(entry.getFile()))
                    .filter(Files::exists)
                    .map(path -> uncheck(() -> prepareJijModFile(tempDir, secureJar.name(), path)));
            })
            .toList();
        List<Path> moduleSafeJars = SplitPackageMerger.handleSplitPackages(discoveredRemapped);
        return moduleSafeJars.stream()
            .map(path -> ConnectorLocator.createConnectorModFile(path, this))
            .toList();
    }

    private Path prepareJijModFile(Path tempDir, String parentName, Path path) throws IOException {
        Files.createDirectories(tempDir);

        // TODO Remap nested jars along with the main jar?
        String parentNameWithoutExt = parentName.split("\\.(?!.*\\.)")[0];
        // Extract JiJ
        Path extracted = tempDir.resolve(parentNameWithoutExt + "$" + path.getFileName().toString());
        ConnectorUtil.cache("1", path, extracted, () -> Files.copy(path, extracted));

        return uncheck(() -> ConnectorLocator.cacheRemapJar(extracted.toFile()));
    }

    @Override
    public String name() {
        return "connector-jij";
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
