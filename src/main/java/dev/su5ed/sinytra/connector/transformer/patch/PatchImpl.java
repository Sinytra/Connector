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
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

class PatchImpl implements Patch {
    private static final String MIXIN_ANN = "Lorg/spongepowered/asm/mixin/Mixin;";
    static final String INJECT_ANN = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    static final String REDIRECT_ANN = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String OVERWRITE_ANN = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String MODIFY_VARIABLE_ANN = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
    static final String MODIFY_ARG_ANN = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
    // TODO why did I put this here? :thinkies:
    static final String ACCESSOR_ANN = "Lorg/spongepowered/asm/mixin/gen/Accessor;";

    private static final Logger LOGGER = LogUtils.getLogger();
    static final Marker PATCHER = MarkerFactory.getMarker("MIXINPATCH");

    private final Set<String> targetClasses;
    private final Set<MethodMatcher> targetMethods;
    private final Set<InjectionPointMatcher> targetInjectionPoints;
    private final Predicate<String> targetAnnotations;
    private final Predicate<Map<String, AnnotationValueHandle<?>>> targetAnnotationValues;
    private final List<MethodTransform> transforms;

    public PatchImpl(Set<String> targetClasses, Set<MethodMatcher> targetMethods, Set<InjectionPointMatcher> targetInjectionPoints, Predicate<String> targetAnnotations, Predicate<Map<String, AnnotationValueHandle<?>>> targetAnnotationValues, List<MethodTransform> transforms) {
        this.targetClasses = targetClasses;
        this.targetMethods = targetMethods;
        this.targetInjectionPoints = targetInjectionPoints;
        this.targetAnnotations = targetAnnotations;
        this.targetAnnotationValues = targetAnnotationValues;
        this.transforms = transforms;
    }

    @Override
    public boolean apply(ClassNode classNode) {
        boolean applied = false;
        PatchContext context = new PatchContext();
        if (checkClassTarget(classNode, this.targetClasses)) {
            for (MethodTransform transform : this.transforms) {
                applied |= transform.apply(classNode);
            }
            for (MethodNode method : classNode.methods) {
                Pair<AnnotationNode, Map<String, AnnotationValueHandle<?>>> annotationValues = checkMethodTarget(method).orElse(null);
                if (annotationValues != null) {
                    for (MethodTransform transform : this.transforms) {
                        applied |= transform.apply(classNode, method, annotationValues.getFirst(), annotationValues.getSecond(), context);
                    }
                }
            }
            context.run();
        }
        return applied;
    }

    private record InjectionPointMatcher(@Nullable String value, String target) {
        public boolean test(String value, String target) {
            return this.target.equals(target) && (this.value == null || this.value.equals(value));
        }
    }

    private static boolean checkClassTarget(ClassNode classNode, Set<String> targets) {
        if (targets.isEmpty()) {
            return true;
        }
        else if (classNode.invisibleAnnotations != null) {
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

    private Optional<Pair<AnnotationNode, Map<String, AnnotationValueHandle<?>>>> checkMethodTarget(MethodNode method) {
        if (method.visibleAnnotations != null) {
            for (AnnotationNode annotation : method.visibleAnnotations) {
                if (this.targetAnnotations.test(annotation.desc)) {
                    Map<String, AnnotationValueHandle<?>> values = checkAnnotation(method, annotation).orElse(null);
                    if (values != null && this.targetAnnotationValues.test(values)) {
                        return Optional.of(Pair.of(annotation, values));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Map<String, AnnotationValueHandle<?>>> checkAnnotation(MethodNode method, AnnotationNode annotation) {
        if (annotation.desc.equals(OVERWRITE_ANN)) {
            if (this.targetMethods.isEmpty() || this.targetMethods.stream().anyMatch(matcher -> matcher.matches(method.name, method.desc))) {
                return Optional.of(Map.of());
            }
        }
        else if (annotation.desc.equals(INJECT_ANN) || annotation.desc.equals(REDIRECT_ANN) || annotation.desc.equals(MODIFY_VARIABLE_ANN) || annotation.desc.equals(MODIFY_ARG_ANN)) {
            return PatchImpl.<List<String>>findAnnotationValue(annotation.values, "method")
                .flatMap(value -> {
                    for (String target : value.get()) {
                        // Remove owner class; it is always the same as the mixin target
                        if (target.contains(";")) {
                            target = target.substring(target.indexOf(';') + 1);
                        }
                        int targetDescIndex = target.indexOf('(');
                        String targetName = targetDescIndex == -1 ? target : target.substring(0, targetDescIndex);
                        String targetDesc = targetDescIndex == -1 ? null : target.substring(targetDescIndex);
                        if (this.targetMethods.isEmpty() || this.targetMethods.stream().anyMatch(matcher -> matcher.matches(targetName, targetDesc))) {
                            Map<String, AnnotationValueHandle<?>> map = new HashMap<>();
                            map.put("method", value);
                            if (annotation.desc.equals(MODIFY_ARG_ANN)) {
                                map.put("index", PatchImpl.<Integer>findAnnotationValue(annotation.values, "index").orElse(null));
                            }
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

    public static <T> Optional<AnnotationValueHandle<T>> findAnnotationValue(@Nullable List<Object> values, String key) {
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

    record ModifyInjectionPointTransform(String replacementTargetDesc) implements MethodTransform {
        @Override
        public boolean apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
            AnnotationValueHandle<String> handle = (AnnotationValueHandle<String>) Objects.requireNonNull(annotationValues.get("target"), "Missing target handle, did you specify the target descriptor?");
            LOGGER.info(PATCHER, "Changing mixin method target {}.{} to {}", classNode.name, methodNode.name, this.replacementTargetDesc);
            handle.set(this.replacementTargetDesc);
            return true;
        }
    }

    record ModifyTargetTransform(List<String> replacementMethods) implements MethodTransform {
        @Override
        public boolean apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
            LOGGER.info(PATCHER, "Redirecting mixin {}.{} to {}", classNode.name, methodNode.name, this.replacementMethods);
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

    record ChangeModifiedVariableIndex(IntUnaryOperator operator) implements MethodTransform {
        @Override
        public boolean apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
            AnnotationValueHandle<Integer> index = PatchImpl.<Integer>findAnnotationValue(annotation.values, "index").orElseThrow();
            if (index.get() > -1) {
                int newIndex = operator.applyAsInt(index.get());
                index.set(newIndex);
                return true;
            }
            return false;
        }
    }

    record ModifyMixinMethodParams(Consumer<List<Type>> operator, @Nullable LVTFixer lvtFixer) implements MethodTransform {
        @Override
        public boolean apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
            Type[] parameterTypes = Type.getArgumentTypes(methodNode.desc);
            List<Type> list = new ArrayList<>(Arrays.asList(parameterTypes));
            this.operator.accept(list);
            Type[] newParameterTypes = list.toArray(Type[]::new);
            Type returnType = Type.getReturnType(methodNode.desc);
            String newDesc = Type.getMethodDescriptor(returnType, newParameterTypes);
            LOGGER.info(PATCHER, "Changing descriptor of method {}.{}{} to {}", classNode.name, methodNode.name, methodNode.desc, newDesc);
            Int2ObjectMap<Type> insertionIndices = new Int2ObjectOpenHashMap<>();
            Int2ObjectMap<Type> replacementIndices = new Int2ObjectOpenHashMap<>();
            int offset = (methodNode.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;

            int i = 0;
            for (int j = 0; j < newParameterTypes.length && i < methodNode.parameters.size(); j++) {
                Type type = newParameterTypes[j];
                if (!parameterTypes[i].equals(type)) {
                    if (i == j && this.lvtFixer != null) {
                        replacementIndices.put(offset + j, type);
                    }
                    else {
                        insertionIndices.put(j, type);
                        continue;
                    }
                }
                i++;
            }
            if (i != methodNode.parameters.size() && this.lvtFixer == null) {
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
            replacementIndices.forEach((index, type) -> {
                LocalVariableNode localVar = methodNode.localVariables.get(index);
                localVar.desc = type.getDescriptor();
                localVar.signature = null;
            });
            if (!replacementIndices.isEmpty() && this.lvtFixer != null) {
                //noinspection ForLoopReplaceableByForEach
                for (ListIterator<AbstractInsnNode> iterator = methodNode.instructions.iterator(); iterator.hasNext(); ) {
                    AbstractInsnNode insn = iterator.next();
                    if (insn instanceof VarInsnNode varInsn && replacementIndices.containsKey(varInsn.var)) {
                        this.lvtFixer.accept(varInsn.var, varInsn, methodNode.instructions);
                    }
                }
            }
            methodNode.desc = newDesc;
            return true;
        }
    }

    private static class DisableMixins implements MethodTransform {
        public static final MethodTransform INSTANCE = new DisableMixins();
        
        @Override
        public boolean apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
            LOGGER.debug(PATCHER, "Removing mixin method {}.{}{}", classNode.name, methodNode.name, methodNode.desc);
            context.postApply(() -> classNode.methods.remove(methodNode));
            return true;
        }
    }

    static class BuilderImpl implements Builder {
        private final Set<String> targetClasses = new HashSet<>();
        private final Set<MethodMatcher> targetMethods = new HashSet<>();
        private Predicate<String> targetAnnotations;
        private Predicate<Map<String, AnnotationValueHandle<?>>> targetAnnotationValues;
        private final Set<InjectionPointMatcher> targetInjectionPoints = new HashSet<>();
        private final List<MethodTransform> transforms = new ArrayList<>();

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
            this.targetAnnotations = this.targetAnnotations == null ? annotationDescPredicate : this.targetAnnotations.or(annotationDescPredicate);
            return this;
        }

        @Override
        public Builder targetAnnotationValues(Predicate<Map<String, AnnotationValueHandle<?>>> values) {
            this.targetAnnotationValues = this.targetAnnotationValues == null ? values : this.targetAnnotationValues.or(values);
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
            return transform(new ModifyInjectionPointTransform(target));
        }

        @Override
        public Builder modifyParams(Consumer<List<Type>> operator) {
            return modifyParams(operator, null);
        }

        @Override
        public Builder modifyParams(Consumer<List<Type>> operator, @Nullable LVTFixer lvtFixer) {
            return transform(new ModifyMixinMethodParams(operator, lvtFixer));
        }

        @Override
        public Builder modifyTarget(String... methods) {
            return transform(new ModifyTargetTransform(List.of(methods)));
        }

        @Override
        public Builder modifyVariableIndex(IntUnaryOperator operator) {
            return transform(new ChangeModifiedVariableIndex(operator));
        }

        @Override
        public Builder disable() {
            return transform(DisableMixins.INSTANCE);
        }

        @Override
        public Builder transform(MethodTransform transformer) {
            this.transforms.add(transformer);
            return this;
        }

        @Override
        public Patch build() {
            return new PatchImpl(
                Collections.unmodifiableSet(this.targetClasses),
                Collections.unmodifiableSet(this.targetMethods),
                Collections.unmodifiableSet(this.targetInjectionPoints),
                Objects.requireNonNullElseGet(this.targetAnnotations, () -> s -> true),
                Objects.requireNonNullElseGet(this.targetAnnotationValues, () -> v -> true),
                Collections.unmodifiableList(this.transforms)
            );
        }
    }
}
