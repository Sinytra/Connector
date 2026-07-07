package org.sinytra.connector.transformer.runner.discovery;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.jarcontents.JarResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

public class ProbeModDiscoverer {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static List<Path> resolveClassPath(List<Path> paths, Path tempDir) {
        return paths.stream()
            .flatMap(path -> Stream.concat(
                Stream.of(path),
                resolveNestedJars(path, tempDir).stream()
            ))
            .toList();
    }

    private static List<Path> resolveNestedJars(Path path, Path tempDir) {
        try {
            List<Path> output = new ArrayList<>();

            try (JarContents jar = JarContents.ofPath(path)) {
                jar.visitContent("META-INF/jars", (p, res) -> {
                    if (p.endsWith(".jar")) {
                        Path extracted = extractFile(p, res, tempDir);
                        output.add(extracted);

                        resolveNestedJars(extracted, tempDir);
                    }
                });
            }

            return output;
        } catch (Throwable t) {
            throw new RuntimeException("Error resolving nested jars", t);
        }
    }

    // Reused from net.neoforged.fml.loading.moddiscovery.locators.JarInJarDependencyLocator
    /*
     * Copyright (c) Forge Development LLC and contributors
     * SPDX-License-Identifier: LGPL-2.1-only
     */

    private static Path extractFile(String relativePath, JarResource file, Path tempDir) {
        Path tempFile;
        try {
            tempFile = Files.createTempFile(tempDir, "_probe", ".tmp");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create a temporary file in " + tempDir, e);
        }

        // Copy the file to the temp-file, while hashing it to produce its final filename
        Path finalPath;
        try {
            String checksum = extractEmbeddedJarFile(file, relativePath, tempFile);

            // We must maintain the original filename, as it could be used to determine the module name and version
            String filename = relativePath.substring(relativePath.lastIndexOf('/') + 1);
            finalPath = tempDir.resolve(checksum + "/" + filename);
            // If the file already exists, reuse it, since it might already be opened.
            if (!Files.isRegularFile(finalPath)) {
                moveExtractedFileIntoPlace(tempFile, finalPath);
            }

            return finalPath;
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                LOGGER.error("Failed to remove temporary file {}: {}", tempFile, e);
            }
        }
    }

    private static String extractEmbeddedJarFile(JarResource file, String relativePath, Path destination) {
        try (var inStream = file.open(); var outStream = Files.newOutputStream(destination)) {
            if (inStream == null) {
                LOGGER.error("Mod file {} declares {} but does not contain it.", file, relativePath);
                throw new RuntimeException("Mod file " + file + " declares " + relativePath + " but does not contain it.");
            }

            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Missing default JCA algorithm SHA-256.", e);
            }

            var digestOut = new DigestOutputStream(outStream, digest);
            inStream.transferTo(digestOut);

            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            LOGGER.error("Failed to copy file {} from mod file {} to {}", relativePath, file, destination, e);
            throw new UncheckedIOException("Failed to load file " + relativePath, e);
        }
    }

    private static void moveExtractedFileIntoPlace(Path source, Path destination) {
        try {
            Files.createDirectories(destination.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create parent directory for extracted file " + source + " at " + destination, e);
        }

        try {
            try {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to move temporary file " + source + " to its final location " + destination, e);
        }
    }
}
