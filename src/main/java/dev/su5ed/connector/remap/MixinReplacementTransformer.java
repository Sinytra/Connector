package dev.su5ed.connector.remap;

import com.google.gson.*;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fart.api.Transformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.rethrowFunction;

public class MixinReplacementTransformer implements Transformer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Marker ENHANCE = MarkerFactory.getMarker("MIXIN_ENHANCE");

    private static final String MIXIN_ANN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String REDIRECT_ANN = "Lorg/spongepowered/asm/mixin/injection/Redirect;";

    private static final List<Replacement> ENHANCEMENTS = List.of(
            Replacement.removeMixinMethod(
                    "net/fabricmc/fabric/mixin/registry/sync/StructuresToConfiguredStructuresFixMixin",
                    "findUpdatedStructureType"
            ),
            Replacement.replaceMixinMethod(
                    "net/fabricmc/fabric/mixin/client/rendering/ScreenMixin",
                    "injectRenderTooltipLambda",
                    "dev/su5ed/connector/replacement/RenderingApiReplacements"
            ),
            Replacement.removeMixinMethod(
                    "net/fabricmc/fabric/mixin/client/rendering/shader/ShaderProgramMixin",
                    "modifyProgramId"
            ),
            Replacement.redirectMixinTarget(
                    "net/minecraft/client/renderer/entity/BoatRenderer",
                    "render",
                    REDIRECT_ANN,
                    List.of("getModelWithLocation")
            ),
            Replacement.mappingReplacement()
    );
    private static final List<String> REMOVALS = List.of(
            "net/fabricmc/fabric/mixin/registry/sync/client/BlockColorsMixin",
            "net/fabricmc/fabric/mixin/registry/sync/client/ItemColorsMixin",
            "net/fabricmc/fabric/mixin/registry/sync/client/ParticleManagerMixin",

            "net/fabricmc/fabric/mixin/client/rendering/shader/ShaderProgramMixin",
            "net/fabricmc/fabric/mixin/client/rendering/shader/ShaderProgramImportProcessorMixin"
    );

    private final Collection<String> configs;
    private final Set<String> mixins;
    private final Map<String, String> mappings;

    public MixinReplacementTransformer(Collection<String> configs, Set<String> mixins, Map<String, String> mappings) {
        this.configs = configs;
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

    @Override
    public ResourceEntry process(ResourceEntry entry) {
        if (this.configs.contains(entry.getName())) {
            try (Reader reader = new InputStreamReader(new ByteArrayInputStream(entry.getData()))) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String pkg = json.get("package").getAsString();
                String pkgPath = pkg.replace('.', '/') + '/';
                Set.of("mixins", "client", "server").stream()
                        .filter(json::has)
                        .forEach(str -> {
                            JsonArray array = json.getAsJsonArray(str);
                            for (Iterator<JsonElement> it = array.iterator(); it.hasNext(); ) {
                                JsonElement element = it.next();
                                if (REMOVALS.contains(pkgPath + element.getAsString().replace('.', '/'))) {
                                    it.remove();
                                }
                            }
                        });
                try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
                    try (Writer writer = new OutputStreamWriter(byteStream)) {
                        new Gson().toJson(json, writer);
                        writer.flush();
                    }
                    byte[] data = byteStream.toByteArray();
                    return ResourceEntry.create(entry.getName(), entry.getTime(), data);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
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

        static Replacement replaceMixinMethod(String mixinClass, String mixinMethod, String replacementClass) {
            return replaceMixinMethod(mixinClass, mixinMethod, replacementClass, mixinMethod);
        }

        static Replacement replaceMixinMethod(String mixinClass, String mixinMethod, String replacementClass, String replacementMethod) {
            return new ReplaceMixin(mixinClass, mixinMethod, replacementClass, replacementMethod);
        }

        static Replacement removeMixinMethod(String mixinClass, String mixinMethod) {
            return new RemoveMixin(mixinClass, mixinMethod);
        }

        static Replacement redirectMixinTarget(String targetClass, String mixinMethod, String annotation, List<String> replacementMethods) {
            return new RedirectMixin(targetClass, mixinMethod, annotation, replacementMethods);
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

    public interface MethodReplacement extends Replacement {
        String mixinClass();

        String mixinMethod();

        Pair<MethodAction, MethodNode> apply(ClassNode node, MethodNode methodNode);

        @Override
        default boolean handles(ClassNode classNode) {
            return classNode.name.equals(mixinClass());
        }

        @Override
        default void apply(ClassNode node, MixinReplacementTransformer instance) {
            String mixinMethod = mixinMethod();
            int descIndex = mixinMethod.indexOf('(');
            String methodName = descIndex == -1 ? mixinMethod : mixinMethod.substring(0, descIndex);
            String methodDesc = descIndex == -1 ? null : mixinMethod.substring(descIndex);

            List<MethodNode> toRemove = new ArrayList<>();
            for (int i = 0; i < node.methods.size(); i++) {
                MethodNode method = node.methods.get(i);
                if (method.name.equals(methodName) && (methodDesc == null || method.desc.equals(methodDesc))) {
                    Pair<MethodAction, MethodNode> pair = apply(node, method);
                    MethodAction action = pair.getFirst();

                    if (action == MethodAction.REMOVE) {
                        toRemove.add(method);
                    } else if (action == MethodAction.REPLACE) {
                        node.methods.set(i, pair.getSecond());
                    }
                }
            }
            node.methods.removeAll(toRemove);
        }

        enum MethodAction {
            KEEP,
            REPLACE,
            REMOVE;

            static Pair<MethodAction, MethodNode> keep() {
                return Pair.of(KEEP, null);
            }

            static Pair<MethodAction, MethodNode> replace(MethodNode replacement) {
                return Pair.of(REPLACE, replacement);
            }

            static Pair<MethodAction, MethodNode> remove() {
                return Pair.of(REMOVE, null);
            }
        }
    }

    record ReplaceMixin(String mixinClass, String mixinMethod, String replacementClass,
                        String replacementMethod) implements MethodReplacement {
        private static final Map<String, ClassNode> CLASS_CACHE = new ConcurrentHashMap<>();

        @Override
        public Pair<MethodAction, MethodNode> apply(ClassNode classNode, MethodNode methodNode) {
            LOGGER.info(ENHANCE, "Replacing mixin {}.{}", classNode.name, methodNode.name);
            ClassNode replNode = CLASS_CACHE.computeIfAbsent(this.replacementClass, rethrowFunction(c -> {
                ClassReader replReader = new ClassReader(this.replacementClass);
                ClassNode rNode = new ClassNode();
                replReader.accept(rNode, 0);
                return rNode;
            }));

            MethodNode replMethod = replNode.methods.stream().filter(m -> m.name.equals(this.replacementMethod)).findFirst().orElseThrow();
            return MethodAction.replace(replMethod);
        }
    }

    record RemoveMixin(String mixinClass, String mixinMethod) implements MethodReplacement {
        @Override
        public Pair<MethodAction, MethodNode> apply(ClassNode classNode, MethodNode methodNode) {
            LOGGER.info(ENHANCE, "Removing mixin {}.{}", classNode.name, methodNode.name);
            return MethodAction.remove();
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
}
