package dev.su5ed.sinytra.connector.transformer.jar;

import dev.su5ed.sinytra.adapter.patch.util.provider.ClassLookup;
import net.minecraftforge.fart.api.ClassProvider;
import net.minecraftforge.fart.internal.EnhancedClassRemapper;
import net.minecraftforge.fart.internal.EnhancedRemapper;
import net.minecraftforge.srgutils.IMappingFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RenamingClassLookup implements ClassLookup {
    private final ClassProvider upstream;
    private final EnhancedRemapper remapper;

    private final Map<String, Optional<ClassNode>> classCache = new ConcurrentHashMap<>();

    public RenamingClassLookup(ClassProvider upstream, IMappingFile mapping) {
        this.upstream = upstream;
        this.remapper = new EnhancedRemapper(this.upstream, mapping, s -> {});
    }

    @Override
    public Optional<ClassNode> getClass(String name) {
        return this.classCache.computeIfAbsent(name, s -> this.upstream.getClassBytes(name)
            .map(data -> {
                ClassReader reader = new ClassReader(data);
                ClassNode node = new ClassNode();
                ClassRemapper remapper = new EnhancedClassRemapper(node, this.remapper, null);
                reader.accept(remapper, 0);
                return node;
            }));
    }
}
