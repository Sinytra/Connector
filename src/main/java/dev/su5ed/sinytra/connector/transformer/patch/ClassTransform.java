package dev.su5ed.sinytra.connector.transformer.patch;

import org.objectweb.asm.tree.ClassNode;

public interface ClassTransform {
    boolean apply(ClassNode classNode);
}
