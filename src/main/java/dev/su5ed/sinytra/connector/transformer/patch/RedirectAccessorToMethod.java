package dev.su5ed.sinytra.connector.transformer.patch;

import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.selector.MethodContext;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Set;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public record RedirectAccessorToMethod(String value) implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(Patch.ACCESSOR);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationVisitor visitor = methodNode.visitAnnotation(Patch.INVOKER, true);
        visitor.visit("value", this.value);
        visitor.visitEnd();

        methodNode.visibleAnnotations.remove(methodContext.methodAnnotation().unwrap());

        LOGGER.info(MIXINPATCH, "Redirecting accessor {}.{} to invoke method {}", classNode.name, methodNode.name, this.value);

        return Patch.Result.APPLY;
    }
}
