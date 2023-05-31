package dev.su5ed.connector.locator;

import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

public final class EmbeddedDependencies {
    private static final String JIJ_ATTRIBUTE_PREFIX = "Additional-Dependencies-";
    private static final String LANGUAGE_JIJ_DEP = "Language";
    private static final String MOD_JIJ_DEP = "Mod";

    private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
    private static final Path SELF_PATH = uncheck(() -> {
        URL jarLocation = ConnectorLocator.class.getProtectionDomain().getCodeSource().getLocation();
        return Path.of(jarLocation.toURI());
    });
    private static final Attributes ATTRIBUTES = readManifestAttributes();

    public static Stream<Path> locateAdditionalDependencies() {
        try {
            Path languageJij = getJarInJar(LANGUAGE_JIJ_DEP);
            Path modJij = getJarInJar(MOD_JIJ_DEP);
            return Stream.of(languageJij, modJij);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Path getContainedFile(String path) {
        return SELF_PATH.resolve(path);
    }

    private static Path getJarInJar(String name) throws IOException, URISyntaxException {
        String depName = ATTRIBUTES.getValue(JIJ_ATTRIBUTE_PREFIX + name);
        if (depName == null) {
            throw new IllegalArgumentException("Required " + name + " embedded jar not found");
        }
        Path pathInModFile = SELF_PATH.resolve(depName);
        URI filePathUri = new URI("jij:" + pathInModFile.toAbsolutePath().toUri().getRawSchemeSpecificPart()).normalize();
        Map<String, ?> outerFsArgs = ImmutableMap.of("packagePath", pathInModFile);
        FileSystem zipFS = FileSystems.newFileSystem(filePathUri, outerFsArgs);
        return zipFS.getPath("/");
    }

    private static Attributes readManifestAttributes() {
        Path manifestPath = SELF_PATH.resolve(MANIFEST_PATH);
        Manifest manifest;
        try (InputStream is = Files.newInputStream(manifestPath)) {
            manifest = new Manifest(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return manifest.getMainAttributes();
    }

    private EmbeddedDependencies() {}
}
