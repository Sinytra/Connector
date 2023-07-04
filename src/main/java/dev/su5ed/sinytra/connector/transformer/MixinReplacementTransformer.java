package dev.su5ed.sinytra.connector.transformer;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraftforge.fart.api.Transformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableAnnotationNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;
import org.objectweb.asm.tree.TypeAnnotationNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class MixinReplacementTransformer implements Transformer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Marker ENHANCE = MarkerFactory.getMarker("MIXIN_ENHANCE");

    private static final String MIXIN_ANN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String INJECT_ANN = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String REDIRECT_ANN = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String MODIFY_VARIABLE_ANN = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";

    private static final List<Replacement> ENHANCEMENTS = List.of(
        Replacement.redirectMixinTarget(
            "net/minecraft/client/renderer/entity/BoatRenderer",
            "render",
            REDIRECT_ANN,
            List.of("getModelWithLocation")
        ),
        // TODO Add mirror mixin method that injects into ForgeHooks#onPlaceItemIntoWorld for server side behavior
        Replacement.modifyMixinMethodParams(
            "net/minecraft/world/item/ItemStack",
            "useOnBlock",
            INJECT_ANN,
            types -> {
                List<Type> list = new ArrayList<>(Arrays.asList(types));
                list.add(1, Type.getType("Lnet/minecraft/world/item/context/UseOnContext;"));
                return list.toArray(Type[]::new);
            }
        ),
        Replacement.redirectMixinTarget(
            "net/minecraft/world/item/ItemStack",
            "useOnBlock",
            INJECT_ANN,
            List.of("lambda$useOn$5")
        ),
        Replacement.retargetMixinMethod(
            "net/minecraft/client/gui/screens/inventory/EffectRenderingInventoryScreen",
            "renderEffects",
            "Lcom/google/common/collect/Ordering;sortedCopy(Ljava/lang/Iterable;)Ljava/util/List;",
            MODIFY_VARIABLE_ANN,
            "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"
        ),
        Replacement.mappingReplacement()
    );

    private final Set<String> mixins;
    private final Map<String, String> mappings;

    public MixinReplacementTransformer(Set<String> mixins, Map<String, String> mappings) {
        this.mixins = mixins;
        this.mappings = mappings;
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        String className = entry.getClassName();
        if (this.mixins.contains(className)) {
            ClassReader reader = new ClassReader(entry.getData());
            ClassNode node = new ClassNode();
            reader.accept(node, 0);

            List<Replacement> replacements = ENHANCEMENTS.stream()
                .filter(r -> r.handles(node))
                .toList();
            if (applyReplacements(node, replacements)) {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); // TODO Compute frames
                node.accept(writer);
                return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray());
            }
        }
        return entry;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T, U> U findAnnotationValue(List<Object> values, String key, Function<T, U> processor) {
        for (int i = 0; i < values.size(); i += 2) {
            String atKey = (String) values.get(i);
            if (atKey.equals(key)) {
                Object atValue = values.get(i + 1);
                return processor.apply((T) atValue);
            }
        }
        return null;
    }

    @Nullable
    private static void setAnnotationValue(List<Object> values, String key, Object value) {
        for (int i = 0; i < values.size(); i += 2) {
            String atKey = (String) values.get(i);
            if (atKey.equals(key)) {
                values.set(i + 1, value);
            }
        }
    }

    private static boolean targetsType(ClassNode classNode, String type) {
        if (classNode.invisibleAnnotations != null) {
            for (AnnotationNode annotation : classNode.invisibleAnnotations) {
                if (annotation.desc.equals(MIXIN_ANN)) {
                    Boolean value = MixinReplacementTransformer.<List<Type>, Boolean>findAnnotationValue(annotation.values, "value", val -> {
                        for (Type targetType : val) {
                            if (type.equals(targetType.getInternalName())) {
                                return true;
                            }
                        }
                        return false;
                    });
                    if (value == null || !value) {
                        value = MixinReplacementTransformer.<List<String>, Boolean>findAnnotationValue(annotation.values, "targets", val -> {
                            for (String targetType : val) {
                                if (type.equals(targetType)) {
                                    return true;
                                }
                            }
                            return false;
                        });
                    }
                    return value != null && value;
                }
            }
        }
        return false;
    }

    private boolean applyReplacements(ClassNode node, List<Replacement> replacements) {
        if (!replacements.isEmpty()) {
            for (Replacement replacement : replacements) {
                replacement.apply(node, this);
            }
            return true;
        }
        return false;
    }

    interface Replacement {
        boolean handles(ClassNode classNode);

        void apply(ClassNode classNode, MixinReplacementTransformer instance);

        static Replacement redirectMixinTarget(String targetClass, String mixinMethod, String annotation, List<String> replacementMethods) {
            return new RedirectMixin(targetClass, mixinMethod, annotation, replacementMethods);
        }

        static Replacement retargetMixinMethod(String targetClass, String targetMethod, String targetDesc, String annotation, String replacementTargetDesc) {
            return new RetargetMixin(targetClass, targetMethod, targetDesc, annotation, replacementTargetDesc);
        }

        static Replacement modifyMixinMethodParams(String targetClass, String mixinMethod, String annotation, Function<Type[], Type[]> descFunc) {
            return new ModifyMixinMethodParams(targetClass, mixinMethod, annotation, descFunc);
        }

        static Replacement mappingReplacement() {
            return new MapMixin();
        }
    }

    public record MapMixin() implements Replacement {
        @Override
        public boolean handles(ClassNode classNode) {
            return true;
        }

        @Override
        public void apply(ClassNode classNode, MixinReplacementTransformer instance) {
            for (MethodNode method : classNode.methods) {
                if (method.visibleAnnotations != null) {
                    for (AnnotationNode annotation : method.visibleAnnotations) {
                        if (annotation.values != null) {
                            Boolean remap = MixinReplacementTransformer.findAnnotationValue(annotation.values, "remap", Function.identity());
                            if (remap != null && !remap) {
                                LOGGER.info(ENHANCE, "Renaming mixin {}.{}", classNode.name, method.name);
                                List<String> targetMethods = MixinReplacementTransformer.findAnnotationValue(annotation.values, "method", Function.identity());
                                List<String> remapped = targetMethods.stream().map(str -> instance.mappings.getOrDefault(str, str)).toList();
                                MixinReplacementTransformer.setAnnotationValue(annotation.values, "method", remapped);
                                LOGGER.info(ENHANCE, "Renamed {} -> {}", targetMethods, remapped);
                            }
                        }
                    }
                }
            }
        }
    }

    public interface TargetedMethodReplacement extends Replacement {
        String targetClass();

        String targetMethod();

        String annotation();

        void apply(ClassNode node, MethodNode methodNode, List<String> targetMethods);

        @Override
        default boolean handles(ClassNode classNode) {
            return targetsType(classNode, targetClass());
        }

        @Override
        default void apply(ClassNode classNode, MixinReplacementTransformer instance) {
            String annotationDesc = annotation();
            String targetMethod = targetMethod();
            int descIndex = targetMethod.indexOf('(');
            String wantedName = descIndex == -1 ? targetMethod : targetMethod.substring(0, descIndex);
            String wantedDesc = descIndex == -1 ? null : targetMethod.substring(descIndex);

            for (int i = 0; i < classNode.methods.size(); i++) {
                MethodNode method = classNode.methods.get(i);
                if (method.visibleAnnotations != null) {
                    for (AnnotationNode annotation : method.visibleAnnotations) {
                        if (annotation.desc.equals(annotationDesc)) {
                            List<String> targetMethods = findAnnotationValue(annotation.values, "method", Function.identity());
                            for (String target : targetMethods) {
                                int targetDescIndex = targetMethod.indexOf('(');
                                String targetName = targetDescIndex == -1 ? target : target.substring(0, targetDescIndex);
                                String targetDesc = targetDescIndex == -1 ? null : target.substring(targetDescIndex);
                                if (wantedName.equals(targetName) && (wantedDesc == null || wantedDesc.equals(targetDesc))) {
                                    apply(classNode, method, targetMethods);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    record RedirectMixin(String targetClass, String targetMethod, String annotation, List<String> replacementMethods) implements TargetedMethodReplacement {
        @Override
        public void apply(ClassNode node, MethodNode method, List<String> targetMethods) {
            LOGGER.info(ENHANCE, "Redirecting mixin {}.{} to {}", node.name, method.name, this.replacementMethods);
            targetMethods.clear();
            targetMethods.addAll(this.replacementMethods);
        }
    }

    public record RetargetMixin(String targetClass, String targetMethod, String targetDesc, String annotation, String replacementTargetDesc) implements TargetedMethodReplacement {
        @Override
        public void apply(ClassNode node, MethodNode methodNode, List<String> targetMethods) {
            String annotationDesc = annotation();
            for (AnnotationNode annotation : methodNode.visibleAnnotations) {
                if (annotation.desc.equals(annotationDesc)) {
                    AnnotationNode at = MixinReplacementTransformer.findAnnotationValue(annotation.values, "at", val -> val instanceof List<?> list ? (AnnotationNode) list.get(0) : (AnnotationNode) val);
                    String target = findAnnotationValue(at.values, "target", Function.identity());
                    if (this.targetDesc.equals(target)) {
                        LOGGER.info("Retargeting mixin method {}.{} to {}", node.name, methodNode.name, this.replacementTargetDesc);
                        setAnnotationValue(at.values, "target", this.replacementTargetDesc);
                    }
                }
            }
        }
    }

    public record ModifyMixinMethodParams(String targetClass, String targetMethod, String annotation, Function<Type[], Type[]> descFunc) implements TargetedMethodReplacement {

        @Override
        public void apply(ClassNode node, MethodNode methodNode, List<String> targetMethods) {
            Type[] parameterTypes = Type.getArgumentTypes(methodNode.desc);
            Type[] newParameterTypes = descFunc.apply(parameterTypes);
            Type returnType = Type.getReturnType(methodNode.desc);
            String newDesc = Type.getMethodDescriptor(returnType, newParameterTypes);
            LOGGER.info(ENHANCE, "Changing descriptor of method {}.{}{} to {}", node.name, methodNode.name, methodNode.desc, newDesc);
            Int2ObjectMap<Type> insertionIndices = new Int2ObjectOpenHashMap<>();
            int offset = (methodNode.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;

            int i = 0;
            for (int j = 0; j < newParameterTypes.length && i < methodNode.parameters.size(); j++) {
                Type type = newParameterTypes[j];
                if (!parameterTypes[i].equals(type)) {
                    insertionIndices.put(j, type);
                    continue;
                }
                i++;
            }
            if (i != methodNode.parameters.size()) {
                throw new RuntimeException("Unable to patch LVT capture, incompatible parameters");
            }
            insertionIndices.forEach((index, type) -> {
                ParameterNode newParameter = new ParameterNode(null, Opcodes.ACC_SYNTHETIC);
                if (index < methodNode.parameters.size()) methodNode.parameters.add(index, newParameter);
                else methodNode.parameters.add(newParameter);

                int localIndex = offset + index;
                for (LocalVariableNode localVariable : methodNode.localVariables) {
                    if (localVariable.index >= localIndex) {
                        localVariable.index++;
                    }
                }
                // TODO All visible/invisible annotations
                if (methodNode.invisibleParameterAnnotations != null) {
                    List<List<AnnotationNode>> annotations = new ArrayList<>(Arrays.asList(methodNode.invisibleParameterAnnotations));
                    if (index < annotations.size()) {
                        annotations.add(index, null);
                        methodNode.invisibleParameterAnnotations = (List<AnnotationNode>[]) annotations.toArray(List[]::new);
                        methodNode.invisibleAnnotableParameterCount = annotations.size();
                    }
                }
                if (methodNode.invisibleTypeAnnotations != null) {
                    List<TypeAnnotationNode> invisibleTypeAnnotations = methodNode.invisibleTypeAnnotations;
                    for (int j = 0; j < invisibleTypeAnnotations.size(); j++) {
                        TypeAnnotationNode typeAnnotation = invisibleTypeAnnotations.get(j);
                        TypeReference ref = new TypeReference(typeAnnotation.typeRef);
                        int typeIndex = ref.getFormalParameterIndex();
                        if (ref.getSort() == TypeReference.METHOD_FORMAL_PARAMETER && typeIndex >= index) {
                            invisibleTypeAnnotations.set(j, new TypeAnnotationNode(TypeReference.newFormalParameterReference(typeIndex + 1).getValue(), typeAnnotation.typePath, typeAnnotation.desc));
                        }
                    }
                }
                if (methodNode.visibleLocalVariableAnnotations != null) {
                    for (LocalVariableAnnotationNode localVariableAnnotation : methodNode.visibleLocalVariableAnnotations) {
                        List<Integer> annotationIndices = localVariableAnnotation.index;
                        for (int j = 0; j < annotationIndices.size(); j++) {
                            Integer annoIndex = annotationIndices.get(j);
                            if (annoIndex >= localIndex) {
                                annotationIndices.set(j, annoIndex + 1);
                            }
                        }
                    }
                }
                for (AbstractInsnNode insn : methodNode.instructions) {
                    if (insn instanceof VarInsnNode varInsnNode && varInsnNode.var >= localIndex) {
                        varInsnNode.var++;
                    }
                }
            });
            methodNode.desc = newDesc;
        }
    }
}
