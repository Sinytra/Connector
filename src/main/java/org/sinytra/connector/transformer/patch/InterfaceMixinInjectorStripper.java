package org.sinytra.connector.transformer.patch;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.ClassTransform;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Removes injector handlers declared by interface mixins.
 *
 * <p>Mixin picks its applicator from the mixin's own access flags. For an interface mixin that is
 * {@code MixinApplicatorInterface}, which copies method declarations only and rejects every injector
 * with "not supported on interface mixin method". Converting the mixin into a class does not help
 * either, since a class mixin may not target an interface. Dropping the handler is therefore the only
 * option, matching the relaxed injection requirements used on Mohist: the mod loads and the affected
 * hook stays inert. Every removed handler is logged at WARN.
 */
public class InterfaceMixinInjectorStripper implements ClassTransform {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> INJECTOR_DESCRIPTORS = Set.of(
        "Lorg/spongepowered/asm/mixin/injection/Inject;",
        "Lorg/spongepowered/asm/mixin/injection/Redirect;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
        "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;",
        "Lcom/llamalad7/mixinextras/injector/ModifyReceiver;",
        "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
        "Lcom/llamalad7/mixinextras/injector/WrapOperation;",
        "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;"
    );

    @Override
    public Patch.Result apply(ClassNode classNode, @Nullable AnnotationValueHandle<?> annotation, PatchContext context) {
        if ((classNode.access & Opcodes.ACC_INTERFACE) == 0) {
            return Patch.Result.PASS;
        }

        List<String> dropped = new ArrayList<>();
        classNode.methods.removeIf(method -> {
            if (!isInjector(method.visibleAnnotations) && !isInjector(method.invisibleAnnotations)) {
                return false;
            }
            dropped.add(method.name + method.desc);
            return true;
        });

        if (dropped.isEmpty()) {
            return Patch.Result.PASS;
        }
        LOGGER.warn("Removing {} injector(s) from interface mixin {}: the mixin applicator for interfaces "
            + "does not support them", dropped.size(), classNode.name);
        for (String method : dropped) {
            LOGGER.warn("  - {}", method);
        }
        return Patch.Result.APPLY;
    }

    private static boolean isInjector(@Nullable List<AnnotationNode> annotations) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode annotation : annotations) {
            if (INJECTOR_DESCRIPTORS.contains(annotation.desc)) {
                return true;
            }
        }
        return false;
    }
}
