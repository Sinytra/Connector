package org.sinytra.connector.transformer.patch;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.ClassTransform;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;

/**
 * Promotes non-public methods declared by interface mixins to public.
 *
 * <p>{@code MixinPreProcessorInterface} rejects private and protected methods with
 * "Interface mixin contains a non-public method". Mixin 0.12 accepts Java 9 private interface
 * methods and mods use them for injector handlers, so turning such a method into a default method
 * keeps the handler body intact while satisfying 0.8.x. Static methods are already allowed and are
 * left untouched.
 */
public class InterfaceMixinAccessWidener implements ClassTransform {
    @Override
    public Patch.Result apply(ClassNode classNode, @Nullable AnnotationValueHandle<?> annotation, PatchContext context) {
        if ((classNode.access & Opcodes.ACC_INTERFACE) == 0) {
            return Patch.Result.PASS;
        }

        boolean applied = false;
        for (MethodNode method : classNode.methods) {
            if ((method.access & Opcodes.ACC_PUBLIC) != 0 || (method.access & Opcodes.ACC_STATIC) != 0) {
                continue;
            }
            method.access = (method.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
            applied = true;
        }
        return applied ? Patch.Result.APPLY : Patch.Result.PASS;
    }
}
