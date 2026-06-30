package org.sinytra.connector.transformer.runner.discovery;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class ProbeModDiscoverer {

    public static List<Path> resolveClassPath(List<Path> paths) {
        return paths.stream()
            .flatMap(path -> Stream.concat(
                Stream.of(path),
                resolveNestedJars(path).stream()
            ))
            .toList();
    }

    private static List<Path> resolveNestedJars(Path path) {
        try {
//            JarContents jar = JarContents.ofPath(path);
//            Path nestedJarDir = jar.get("META-INF/jars");
//            if (!Files.exists(nestedJarDir)) {
//                return List.of();
//            }
//            return Files.walk(nestedJarDir)
//                .filter(p -> p.getFileName().toString().endsWith(".jar"))
//                .flatMap(p -> Stream.concat(
//                    Stream.of(p),
//                    resolveNestedJars(p).stream()
//                ))
//                .toList();
            throw new UnsupportedOperationException(); // FIXME
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
