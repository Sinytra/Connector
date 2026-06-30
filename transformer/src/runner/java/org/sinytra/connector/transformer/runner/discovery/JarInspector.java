package org.sinytra.connector.transformer.runner.discovery;

import com.google.common.base.Suppliers;
import com.google.common.collect.Multimap;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.NestedJarEntry;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.jarcontents.JarResource;
import org.sinytra.connector.transformer.jar.JarTransformer;
import org.sinytra.connector.transformer.transform.TransformerUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.sinytra.connector.transformer.transform.TransformerUtil.uncheck;

public class JarInspector {
    private final JarTransformer transformer;
    private final Path tempDir;

    public JarInspector(JarTransformer transformer, Path tempDir) {
        this.transformer = transformer;
        this.tempDir = tempDir;
    }

    public Stream<JarTransformer.TransformableJar> discoverNestedJarsRecursive(
        JarTransformer.TransformableJar parent,
        Collection<NestedJarEntry> jars,
        Multimap<JarTransformer.TransformableJar, JarTransformer.TransformableJar> parentToChildren
    ) {
        try (JarContents jar = JarContents.ofPath(parent.input().toPath())) {
            return jars.stream()
                .filter(entry -> jar.containsFile(entry.getFile()))
                .flatMap(entry -> {
                    String parentName = jar.getPrimaryPath().getFileName().toString();
                    String fileName = List.of(entry.getFile().split("/")).getLast();
                    JarResource resource = jar.get(entry.getFile());

                    JarTransformer.TransformableJar txJar = uncheck(() -> prepareNestedJar(parentName, fileName, resource));
                    parentToChildren.put(parent, txJar);
                    LoaderModMetadata metadata = txJar.modPath().metadata().modMetadata();
                    return Stream.concat(Stream.of(txJar), discoverNestedJarsRecursive(txJar, metadata.getJars(), parentToChildren));
                });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private JarTransformer.TransformableJar prepareNestedJar(String parentName, String resName, JarResource resource) {
        String parentNameWithoutExt = parentName.split("\\.(?!.*\\.)")[0];
        // Extract JiJ
        Path extracted = this.tempDir.resolve(parentNameWithoutExt + "$" + resName);
        Supplier<byte[]> data = Suppliers.memoize(() -> uncheck(resource::readAllBytes));
        TransformerUtil.cache(data, extracted, () -> Files.write(extracted, data.get()), "1.0");

        return uncheck(() -> this.transformer.cacheTransformableJar(extracted.toFile()));
    }
}
