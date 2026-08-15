package org.sinytra.connector.transformer.transform;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fart.api.Transformer;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class JarSignatureStripper implements Transformer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public ResourceEntry process(ResourceEntry entry) {
        String name = entry.getName();
        return name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".SF")) ? null : entry;
    }

    @Override
    public ManifestEntry process(ManifestEntry entry) {
        Manifest manifest = new Manifest();
        try (InputStream is = new ByteArrayInputStream(entry.getData())) {
            manifest.read(is);
            processManifest(manifest);
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            manifest.write(byteStream);
            return ManifestEntry.create(entry.getTime(), byteStream.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Error writing manifest", e);
        }
    }

    // Remove Automatic Module Name from libraries to force giving them a unique module name
    // and avoid FML silently ignoring those with duplicate names
    public static void processJarInPlace(Path path) {
        try(FileSystem fs = FileSystems.newFileSystem(path)) {
            Path mfPath = fs.getPath("META-INF/MANIFEST.MF");
            if (!Files.exists(mfPath)) return;

            Manifest manifest = new Manifest();
            try (InputStream ins = Files.newInputStream(mfPath)) {
                manifest.read(ins);
            }
            processManifest(manifest);

            try (OutputStream os = Files.newOutputStream(mfPath)) {
                manifest.write(os);
            }
        } catch (Exception e) {
            LOGGER.error("Error stripping jar signature from {}", path, e);
        }
    }

    private static void processManifest(Manifest manifest) {
        manifest.getEntries().clear();
        manifest.getMainAttributes().remove(new Attributes.Name("Automatic-Module-Name"));
    }
}
