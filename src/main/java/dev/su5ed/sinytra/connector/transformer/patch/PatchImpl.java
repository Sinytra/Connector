package dev.su5ed.sinytra.connector.transformer.patch;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraftforge.coremod.api.ASMAPI;
import org.jetbrains.annotations.Nullable;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

class PatchImpl implements Patch {
    private static final String MIXIN_ANN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String INJECT_ANN = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    public static final String REDIRECT_ANN = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String OVERWRITE_ANN = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String MODIFY_VARIABLE_ANN = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Marker PATCHER = MarkerFactory.getMarker("MIXINPATCH");

    private final Set<String> targetClasses;
    private final Set<MethodMatcher> targetMethods;
    private final Set<InjectionPointMatcher> targetInjectionPoints;
    private final Predicate<String> targetAnnotations;
    private final List<Transform> transforms;

    public PatchImpl(Set<String> targetClasses, Set<MethodMatcher> targetMethods, Set<InjectionPointMatcher> targetInjectionPoints, Predicate<String> targetAnnotations, List<Transform> transforms) {
        this.targetClasses = targetClasses;
        this.targetMethods = targetMethods;
        this.targetInjectionPoints = targetInjectionPoints;
        this.targetAnnotations = targetAnnotations;
        this.transforms = transforms;
    }

    @Override
    public boolean apply(ClassNode classNode) {
        boolean applied = false;
        PatchContext context = new PatchContext();
        if (checkClassTarget(classNode, this.targetClasses)) {
            for (MethodNode method : classNode.methods) {
                Pair<AnnotationNode, Map<String, AnnotationValueHandle<?>>> annotationValues = checkMethodTarget(method, this.targetAnnotations).orElse(null);
                if (annotationValues != null) {
                    for (Transform transform : this.transforms) {
                        applied |= transform.apply(classNode, method, annotationValues.getFirst(), annotationValues.getSecond(), context);
                    }
                }
            }
            for (Runnable runnable : context.postApply) {
                runnable.run();
            }
        }
        return applied;
    }

    private static class PatchContext {
        private final List<Runnable> postApply = new ArrayList<>();

        public void postApply(Runnable consumer) {
            this.postApply.add(consumer);
        }
    }

    private record InjectionPointMatcher(@Nullable String value, String target) {
        public boolean test(String value, String target) {
            return this.target.equals(target) && (this.value == null || this.value.equals(value));
        }
    }

    private static boolean checkClassTarget(ClassNode classNode, Set<String> targets) {
        if (classNode.invisibleAnnotations != null) {
            for (AnnotationNode annotation : classNode.invisibleAnnotations) {
                if (annotation.desc.equals(MIXIN_ANN)) {
                    return PatchImpl.<List<Type>>findAnnotationValue(annotation.values, "value")
                        .flatMap(types -> {
                            for (Type targetType : types.get()) {
                                if (targets.contains(targetType.getInternalName())) {
                                    return Optional.of(true);
                                }
                            }
                            return Optional.empty();
                        })
                        .or(() -> PatchImpl.<List<String>>findAnnotationValue(annotation.values, "targets")
                            .map(types -> {
                                for (String targetType : types.get()) {
                                    if (targets.contains(targetType)) {
                                        return true;
                                    }
                                }
                                return false;
                            }))
                        .orElse(false);
                }
            }
        }
        return false;
    }

    private Optional<Pair<AnnotationNode, Map<String, AnnotationValueHandle<?>>>> checkMethodTarget(MethodNode method, Predicate<String> annotationFilter) {
        if (method.visibleAnnotations != null) {
            for (AnnotationNode annotation : method.visibleAnnotations) {
                if (annotationFilter.test(annotation.desc)) {
                    Map<String, AnnotationValueHandle<?>> node = checkAnnotation(method, annotation).orElse(null);
                    if (node != null) {
                        return Optional.of(Pair.of(annotation, node));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Map<String, AnnotationValueHandle<?>>> checkAnnotation(MethodNode method, AnnotationNode annotation) {
        if (annotation.desc.equals(OVERWRITE_ANN)) {
            if (this.targetMethods.stream().anyMatch(matcher -> matcher.matches(method.name, method.desc))) {
                return Optional.of(Map.of());
            }
        }
        else if (annotation.desc.equals(INJECT_ANN) || annotation.desc.equals(REDIRECT_ANN) || annotation.desc.equals(MODIFY_VARIABLE_ANN)) {
            return PatchImpl.<List<String>>findAnnotationValue(annotation.values, "method")
                .flatMap(value -> {
                    for (String target : value.get()) {
                        int targetDescIndex = target.indexOf('(');
                        String targetName = targetDescIndex == -1 ? target : target.substring(0, targetDescIndex);
                        String targetDesc = targetDescIndex == -1 ? null : target.substring(targetDescIndex);
                        if (this.targetMethods.stream().anyMatch(matcher -> matcher.matches(targetName, targetDesc))) {
                            Map<String, AnnotationValueHandle<?>> map = new HashMap<>();
                            map.put("method", value);
                            if (!this.targetInjectionPoints.isEmpty()) {
                                Map<String, AnnotationValueHandle<?>> injectCheck = checkInjectionPoint(annotation).orElse(null);
                                if (injectCheck != null) {
                                    map.putAll(injectCheck);
                                    return Optional.of(map);
                                }
                            }
                            else {
                                return Optional.of(map);
                            }
                        }
                    }
                    return Optional.empty();
                });
        }
        return Optional.empty();
    }

    private Optional<Map<String, AnnotationValueHandle<?>>> checkInjectionPoint(AnnotationNode annotation) {
        return PatchImpl.findAnnotationValue(annotation.values, "at")
            .map(handle -> {
                Object value = handle.get();
                return value instanceof List<?> list ? (AnnotationNode) list.get(0) : (AnnotationNode) value;
            })
            .flatMap(node -> {
                String value = PatchImpl.<String>findAnnotationValue(node.values, "value").map(AnnotationValueHandle::get).orElse(null);
                AnnotationValueHandle<String> target = PatchImpl.<String>findAnnotationValue(node.values, "target").orElse(null);
                if (target != null && this.targetInjectionPoints.stream().anyMatch(pred -> pred.test(value, target.get()))) {
                    return Optional.of(Map.of("target", target));
                }
                return Optional.empty();
            });
    }

    private static <T> Optional<AnnotationValueHandle<T>> findAnnotationValue(@Nullable List<Object> values, String key) {
        if (values != null) {
            for (int i = 0; i < values.size(); i += 2) {
                String atKey = (String) values.get(i);
                if (atKey.equals(key)) {
                    int index = i + 1;
                    return Optional.of(new AnnotationValueHandle<>(values, index));
                }
            }
        }
        return Optional.empty();
    }

    static class AnnotationValueHandle<T> {
        private final List<Object> origin;
        private final int index;

        public AnnotationValueHandle(List<Object> origin, int index) {
            this.origin = origin;
            this.index = index;
        }

        @SuppressWarnings("unchecked")
        public T get() {
            return (T) this.origin.get(index);
        }

        public void set(T value) {
            this.origin.set(index, value);
        }
    }

    static class MethodMatcher {
        private final String name;
        private final String desc;

        public MethodMatcher(String method) {
            int descIndex = method.indexOf('(');
            this.name = descIndex == -1 ? method : method.substring(0, descIndex);
            this.desc = descIndex == -1 ? null : method.substring(descIndex);
        }

        public boolean matches(String name, String desc) {
            return this.name.equals(name) && (this.desc == null || this.desc.equals(desc));
        }
    }

    interface Transform {
        boolean apply(ClassNode node, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context);
    }

    record ModifyInjectionPointTransform(String replacementTargetDesc) implements Transform {
        @Override
        public boolean apply(ClassNode node, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
            AnnotationValueHandle<String> handle = (AnnotationValueHandle<String>) Objects.requireNonNull(annotationValues.get("target"), "Missing target handle, did you specify the target descriptor?");
            LOGGER.info(PATCHER, "Changing mixin method target {}.{} to {}", node.name, methodNode.name, this.replacementTargetDesc);
            handle.set(this.replacementTargetDesc);
            return true;
        }
    }

    record ModifyTargetTransform(List<String> replacementMethods) implements Transform {
        @Override
        public boolean apply(ClassNode node, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
            LOGGER.info(PATCHER, "Redirecting mixin {}.{} to {}", node.name, methodNode.name, this.replacementMethods);
            if (annotation.desc.equals(OVERWRITE_ANN)) {
                if (this.replacementMethods.size() > 1) {
                    throw new IllegalStateException("Cannot determine replacement @Overwrite method name, multiple specified: " + this.replacementMethods);
                }
                methodNode.name = this.replacementMethods.get(0);
            }
            else {
                AnnotationValueHandle<List<String>> targetMethods = (AnnotationValueHandle<List<String>>) annotationValues.get("method");
                targetMethods.set(this.replacementMethods);
            }
            return true;
        }
    }

    record ChangeModifiedVariableIndex(IntUnaryOperator operator) implements Transform {
        @Override
        public boolean apply(ClassNode node, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
            AnnotationValueHandle<Integer> index = PatchImpl.<Integer>findAnnotationValue(annotation.values, "index").orElseThrow();
            if (index.get() > -1) {
                int newIndex = operator.applyAsInt(index.get());
                index.set(newIndex);
                return true;
            }
            return false;
        }
    }

    record ModifyMixinMethodParams(Consumer<List<Type>> operator) implements Transform {
        @Override
        public boolean apply(ClassNode node, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
            Type[] parameterTypes = Type.getArgumentTypes(methodNode.desc);
            List<Type> list = new ArrayList<>(Arrays.asList(parameterTypes));
            this.operator.accept(list);
            Type[] newParameterTypes = list.toArray(Type[]::new);
            Type returnType = Type.getReturnType(methodNode.desc);
            String newDesc = Type.getMethodDescriptor(returnType, newParameterTypes);
            LOGGER.info(PATCHER, "Changing descriptor of method {}.{}{} to {}", node.name, methodNode.name, methodNode.desc, newDesc);
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
            return true;
        }
    }

    record DisableMixins() implements Transform {
        @Override
        public boolean apply(ClassNode node, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
            LOGGER.debug(PATCHER, "Removing mixin method {}.{}{}", node.name, methodNode.name, methodNode.desc);
            context.postApply(() -> node.methods.remove(methodNode));
            return true;
        }
    }

    static class BuilderImpl implements Builder {
        private final Set<String> targetClasses = new HashSet<>();
        private final Set<MethodMatcher> targetMethods = new HashSet<>();
        private Predicate<String> targetAnnotations = s -> true;
        private final Set<InjectionPointMatcher> targetInjectionPoints = new HashSet<>();
        private final List<Transform> transforms = new ArrayList<>();

        @Override
        public Builder targetClass(String... targets) {
            this.targetClasses.addAll(List.of(targets));
            return this;
        }

        @Override
        public Builder targetMethod(String... targets) {
            for (String target : targets) {
                this.targetMethods.add(new MethodMatcher(ASMAPI.mapMethod(target)));
            }
            return this;
        }

        @Override
        public Builder targetMixinType(Predicate<String> annotationDescPredicate) {
            this.targetAnnotations = this.targetAnnotations.and(annotationDescPredicate);
            return this;
        }

        @Override
        public Builder targetInjectionPoint(String target) {
            return targetInjectionPoint(null, target);
        }

        @Override
        public Builder targetInjectionPoint(String value, String target) {
            this.targetInjectionPoints.add(new InjectionPointMatcher(value, target));
            return this;
        }

        @Override
        public Builder modifyInjectionPoint(String target) {
            this.transforms.add(new ModifyInjectionPointTransform(target));
            return this;
        }

        @Override
        public Builder modifyParams(Consumer<List<Type>> operator) {
            this.transforms.add(new ModifyMixinMethodParams(operator));
            return this;
        }

        @Override
        public Builder modifyTarget(String... methods) {
            this.transforms.add(new ModifyTargetTransform(List.of(methods)));
            return this;
        }

        @Override
        public Builder modifyVariableIndex(IntUnaryOperator operator) {
            this.transforms.add(new ChangeModifiedVariableIndex(operator));
            return this;
        }

        @Override
        public Builder disable() {
            this.transforms.add(new DisableMixins());
            return this;
        }

        @Override
        public Patch build() {
            return new PatchImpl(
                Collections.unmodifiableSet(this.targetClasses),
                Collections.unmodifiableSet(this.targetMethods),
                Collections.unmodifiableSet(this.targetInjectionPoints),
                this.targetAnnotations,
                Collections.unmodifiableList(this.transforms)
            );
        }
    }
}
