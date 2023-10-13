package dev.su5ed.sinytra.connector.transformer.patch;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.selector.MethodContext;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

import java.util.Optional;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

// just a ModifyInjectionPoint but I added ordinals
// should probably be added to Adapter tbh
public record AdvancedModifyInjectionPoint(@Nullable String value, String target, boolean resetValues, @Nullable Integer ordinal) implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<AdvancedModifyInjectionPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.optionalFieldOf("value").forGetter(i -> Optional.ofNullable(i.value())),
        Codec.STRING.fieldOf("target").forGetter(AdvancedModifyInjectionPoint::target),
        Codec.INT.optionalFieldOf("ordinal").forGetter(o -> Optional.ofNullable(o.ordinal()))
    ).apply(instance, AdvancedModifyInjectionPoint::new));

    public AdvancedModifyInjectionPoint(String target, int ordinal) {
        this(null, target, true, ordinal);
    }

    public AdvancedModifyInjectionPoint(String value, String target, int ordinal) {
        this(value, target, true, ordinal);
    }

    public AdvancedModifyInjectionPoint(Optional<String> value, String target, boolean resetValues, Optional<Integer> ordinal) {
        this(value.orElse(null), target, resetValues, ordinal.orElse(null));
    }

    public AdvancedModifyInjectionPoint(Optional<String> value, String target, Optional<Integer> ordinal) {
        this(value, target, true, ordinal);
    }

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationHandle annotation = methodContext.injectionPointAnnotationOrThrow();
        if (this.value != null) {
            AnnotationValueHandle<String> handle = annotation.<String>getValue("value").orElseThrow(() -> new IllegalArgumentException("Missing value handle"));
            handle.set(this.value);
        }
        AnnotationValueHandle<String> handle = annotation.<String>getValue("target").orElseThrow(() -> new IllegalArgumentException("Missing target handle, did you specify the target descriptor?"));
        if (this.resetValues) {
            annotation.<Integer>getValue("ordinal").ifPresent(ordinal -> ordinal.set(this.ordinal));
        }
        LOGGER.info(MIXINPATCH, "Changing mixin method target {}.{} to {}", classNode.name, methodNode.name, this.target);
        handle.set(this.target);
        return Patch.Result.APPLY;
    }
}
