package dev.su5ed.sinytra.connector.transformer.patch;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

public interface MethodTransform extends ClassTransform {
    @Override
    default boolean apply(ClassNode classNode) {
        return false;
    }

    boolean apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context);
}
