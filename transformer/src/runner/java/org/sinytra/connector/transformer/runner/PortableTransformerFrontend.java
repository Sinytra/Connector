package org.sinytra.connector.transformer.runner;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.neoforged.fml.jarcontents.JarContents;
import org.sinytra.connector.transformer.jar.JarTransformer;
import org.sinytra.connector.transformer.runner.discovery.JarInspector;
import org.sinytra.connector.transformer.runner.discovery.ProbeModDiscoverer;
import org.sinytra.connector.transformer.transform.TransformerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.launch.MixinBootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.sinytra.connector.transformer.transform.TransformerUtil.rethrowFunction;

public class PortableTransformerFrontend {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortableTransformerFrontend.class);
    private static boolean initialized;

    public record ModPathTuple(List<Path> fabric, List<Path> other) {
    }

    public record TransformOutput(boolean success, String primaryModid) {
    }

    public TransformOutput transform(List<Path> sources, Path outputDir, Path primarySource, Path cleanPath, List<Path> classPath, String gameVersion) throws Exception {
        if (!initialized) {
            MixinBootstrap.init();
            initialized = true;
        }

        Files.createDirectories(outputDir);
        Path tempDir = outputDir.resolve("temp");
        Files.createDirectories(tempDir);

        Path auditLogPath = outputDir.resolve("audit_log.txt");
        Path generatedJarPath = outputDir.resolve("generated.jar");

        PortableRuntimeEnvironment environment = new PortableRuntimeEnvironment(outputDir, auditLogPath, cleanPath, generatedJarPath, gameVersion);
        JarTransformer transformer = new JarTransformer(environment);
        JarInspector inspector = new JarInspector(transformer, tempDir);

        ModPathTuple jars = filterFabricJars(sources);

        List<JarTransformer.TransformableJar> discoveredJars = jars.fabric().stream()
            .map(rethrowFunction(src -> transformer.cacheTransformableJar(src.toFile())))
            .toList();

        JarTransformer.TransformableJar primaryJar = discoveredJars.stream()
            .filter(j -> j.input().toPath().equals(primarySource))
            .findFirst()
            .orElseThrow();
        String primaryModid = primaryJar.modPath().metadata().modMetadata().getId();

        // Prepare jars
        Multimap<JarTransformer.TransformableJar, JarTransformer.TransformableJar> parentToChildren = HashMultimap.create();
        Stream<JarTransformer.TransformableJar> discoveredNestedJars = discoveredJars.stream()
            .flatMap(jar -> {
                LoaderModMetadata metadata = jar.modPath().metadata().modMetadata();
                return inspector.discoverNestedJarsRecursive(jar, metadata.getJars(), parentToChildren);
            });
        List<JarTransformer.TransformableJar> allJars = Stream.concat(discoveredJars.stream(), discoveredNestedJars).toList();

        List<Path> resolvedClassPath = new ArrayList<>(ProbeModDiscoverer.resolveClassPath(classPath, tempDir));
        resolvedClassPath.addAll(jars.other());

        // Run transformation
        try {
            List<JarTransformer.TransformedFabricModPath> results = transformer.transform(allJars, resolvedClassPath);
            boolean success = results.stream()
                .allMatch(result -> result.auditTrail() == null || !result.auditTrail().hasFailingMixins());
            return new TransformOutput(success, primaryModid);
        } catch (Throwable t) {
            LOGGER.error("Failed to transform sources", t);
            return new TransformOutput(false, primaryModid);
        }
    }

    public ModPathTuple filterFabricJars(List<Path> paths) throws IOException {
        List<Path> fabricJars = new ArrayList<>();
        List<Path> otherJars = new ArrayList<>();

        for (Path path : paths) {
            JarContents jar = JarContents.ofPath(path);
            if (jar.containsFile(TransformerUtil.FABRIC_MOD_JSON)) {
                fabricJars.add(path);
            } else {
                otherJars.add(path);
            }
        }

        return new ModPathTuple(fabricJars, otherJars);
    }
}
