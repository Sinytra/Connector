package dev.su5ed.sinytra.connector.transformer;

import net.minecraftforge.fart.api.Transformer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
            manifest.getEntries().clear();
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            manifest.write(byteStream);
            return ManifestEntry.create(entry.getTime(), byteStream.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Error writing manifest", e);
        }
    }
}
