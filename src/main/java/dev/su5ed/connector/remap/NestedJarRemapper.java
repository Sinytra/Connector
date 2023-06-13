package dev.su5ed.connector.remap;

import com.mojang.datafixers.util.Pair;
import dev.su5ed.connector.ConnectorUtil;
import dev.su5ed.connector.loader.ConnectorLoaderModMetadata;
import dev.su5ed.connector.locator.ConnectorLocator;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.NestedJarEntry;
import net.minecraftforge.fart.api.Transformer;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.jarjar.metadata.ContainedJarIdentifier;
import net.minecraftforge.jarjar.metadata.ContainedJarMetadata;
import net.minecraftforge.jarjar.metadata.ContainedVersion;
import net.minecraftforge.jarjar.metadata.Metadata;
import net.minecraftforge.jarjar.metadata.MetadataIOHandler;
import net.minecraftforge.jarjar.selection.util.Constants;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

public class NestedJarRemapper implements Transformer {
    private static final Path TEMP_DIR = FMLPaths.MODSDIR.get().resolve("connector").resolve("temp");
    private static final String JIJ_GROUP = "dev.sinytra.connector.remapped";

    private final String fileName;
    private final List<String> jarPaths;
    private final Map<String, ConnectorLoaderModMetadata> processedJars = new HashMap<>();

    public NestedJarRemapper(String fileName, LoaderModMetadata metadata) {
        this.fileName = fileName;
        this.jarPaths = metadata.getJars().stream().map(NestedJarEntry::getFile).toList();
    }

    @Override
    public ResourceEntry process(ResourceEntry entry) {
        String name = entry.getName();
        if (this.jarPaths.contains(name)) {
            String fileName = name.substring(name.lastIndexOf('/') + 1);
            Pair<ConnectorLocator.FabricModFileMetadata, byte[]> processed = uncheck(() -> processJijModFile(fileName, entry.getData()));
            this.processedJars.put(name, processed.getFirst().modMetadata());
            return ResourceEntry.create(name, entry.getTime(), processed.getSecond());
        }
        return entry;
    }

    @Override
    public Collection<? extends Entry> getExtras() {
        if (!this.processedJars.isEmpty()) {
            List<ContainedJarMetadata> containedJars = this.processedJars.entrySet().stream()
                .map(entry -> {
                    String path = entry.getKey();
                    ConnectorLoaderModMetadata metadata = entry.getValue();
                    ContainedJarIdentifier identifier = new ContainedJarIdentifier(JIJ_GROUP, metadata.getId());
                    String rawVersion = metadata.getNormalizedVersion();
                    ContainedVersion version = new ContainedVersion(VersionRange.createFromVersion("[%s, )".formatted(rawVersion)), new DefaultArtifactVersion(rawVersion));
                    return new ContainedJarMetadata(identifier, version, path, FMLEnvironment.production);
                })
                .toList();
            Metadata jarJarMetadata = new Metadata(containedJars);
            try (InputStream ins = MetadataIOHandler.toInputStream(jarJarMetadata);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                ins.transferTo(out);
                ResourceEntry entry = ResourceEntry.create(Constants.CONTAINED_JARS_METADATA_PATH, ConnectorUtil.ZIP_TIME, out.toByteArray());
                return List.of(entry);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return List.of();
    }

    private Pair<ConnectorLocator.FabricModFileMetadata, byte[]> processJijModFile(String fileName, byte[] data) throws IOException {
        Files.createDirectories(TEMP_DIR);

        String parentNameWithoutExt = this.fileName.split("\\.(?!.*\\.)")[0];
        // Extract JiJ
        Path extracted = TEMP_DIR.resolve(parentNameWithoutExt + "$" + fileName);
        Files.write(extracted, data);

        // Process jar
        Pair<Path, ConnectorLocator.FabricModFileMetadata> remapped = ConnectorLocator.remapJar(extracted.toFile(), false);
        byte[] remappedData = Files.readAllBytes(remapped.getFirst());

        // Clean up
        Files.delete(extracted);
        Files.delete(remapped.getFirst());

        return Pair.of(remapped.getSecond(), remappedData);
    }
}
