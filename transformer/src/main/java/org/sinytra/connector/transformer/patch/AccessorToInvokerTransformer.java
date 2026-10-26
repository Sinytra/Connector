package org.sinytra.connector.transformer.patch;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.api.MethodPatchContext;
import org.sinytra.adapter.api.MethodTransformer;
import org.sinytra.adapter.api.PatchResult;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.slf4j.Logger;

import static org.sinytra.adapter.util.AdapterUtil.MIXINPATCH;

public record AccessorToInvokerTransformer(String value) implements MethodTransformer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public PatchResult apply(MethodPatchContext context) {
        if (!context.injectorAnnotation().desc.equals(MixinAnnotations.ACCESSOR)) {
            return PatchResult.PASS;
        }

        ClassNode classNode = context.classNode();
        MethodNode methodNode = context.methodNode();
        AnnotationVisitor visitor = methodNode.visitAnnotation(MixinAnnotations.INVOKER, true);
        visitor.visit("value", this.value);
        visitor.visitEnd();

        methodNode.visibleAnnotations.remove(context.injectorAnnotation());

        LOGGER.info(MIXINPATCH, "Redirecting accessor {}.{} to invoke method {}", classNode.name, methodNode.name, this.value);

        return PatchResult.APPLY;
    }
}
