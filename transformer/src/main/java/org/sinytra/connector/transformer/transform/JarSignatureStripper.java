package org.sinytra.connector.transformer.transform;

import net.neoforged.art.api.Transformer;

import java.io.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class JarSignatureStripper implements Transformer {
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

    private static void processManifest(Manifest manifest) {
        manifest.getEntries().clear();
        manifest.getMainAttributes().remove(new Attributes.Name("Automatic-Module-Name"));
    }
}
