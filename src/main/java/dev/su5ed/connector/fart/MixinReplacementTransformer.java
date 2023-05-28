package dev.su5ed.connector.fart;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fart.api.Transformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.rethrowFunction;

public class MixinReplacementTransformer implements Transformer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String INJECT_ANN = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String REDIRECT_ANN = "Lorg/spongepowered/asm/mixin/injection/Redirect;";

    private static final List<Replacement> REPLACEMENTS = List.of(
        Replacement.replaceMixinMethod(
            "net/fabricmc/fabric/mixin/entity/event/LivingEntityMixin",
            "dev.su5ed.connector.replacement.EntityApiReplacements",
            "setOccupiedState"
        ),
        Replacement.removeMixinMethod(
            "net/fabricmc/fabric/mixin/entity/event/PlayerEntityMixin",
            "redirectDaySleepCheck"
        ),
        Replacement.removeMixinMethod(
            "net/fabricmc/fabric/mixin/entity/event/ServerPlayerEntityMixin",
            "redirectDaySleepCheck"
        ),
        Replacement.replaceMixinMethod(
            "net/fabricmc/fabric/mixin/entity/event/ServerPlayerEntityMixin",
            "dev.su5ed.connector.replacement.EntityApiReplacements",
            "onTrySleepDirectionCheck"
        ),
        Replacement.redirectMixinTarget(
            "net/fabricmc/fabric/mixin/dimension/EntityMixin",
            "stopEndSpecificBehavior",
            List.of(
                "lambda$changeDimension$12(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/portal/PortalInfo;Ljava/lang/Boolean;)Lnet/minecraft/world/entity/Entity;",
                "changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;",
                "lambda$changeDimension$8(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/portal/PortalInfo;Ljava/lang/Boolean;)Lnet/minecraft/world/entity/Entity"
            )
        ),
        // Redundant fabric fix, https://github.com/MinecraftForge/MinecraftForge/pull/7527
        Replacement.removeMixinMethod(
            "net/fabricmc/fabric/mixin/dimension/RegistryCodecsMixin",
            "modifyCodecLocalVariable"
        ),
        Replacement.removeMixinMethod(
            "net/fabricmc/fabric/mixin/content/registry/AbstractFurnaceBlockEntityMixin",
            "canUseAsFuelRedirect"
        ),
        Replacement.removeMixinMethod(
            "net/fabricmc/fabric/mixin/content/registry/AbstractFurnaceBlockEntityMixin",
            "getFuelTimeRedirect"
        ),
        // TODO Server mixin, too
        Replacement.replaceMixinMethod(
            "net/fabricmc/fabric/mixin/event/lifecycle/client/WorldChunkMixin",
            "onRemoveBlockEntity(Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Object;",
            "dev/su5ed/connector/replacement/LifecycleApiReplacements",
            "onRemoveBlockEntity"
        )
    );

    private final Set<String> mixins;

    public MixinReplacementTransformer(Set<String> mixins) {
        this.mixins = mixins;
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        String className = entry.getClassName();
        if (this.mixins.contains(className)) {
            ClassReader reader = new ClassReader(entry.getData());
            ClassNode node = new ClassNode();
            reader.accept(node, 0);

            List<Replacement> replacements = REPLACEMENTS.stream()
                .filter(r -> className.equals(r.mixinClass()))
                .toList();
            boolean replaced = applyReplacements(node, replacements);
            boolean enhanced = applyEnhancements(node);

            if (replaced || enhanced) {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                node.accept(writer);
                return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray());
            }
        }
        return entry;
    }

    private boolean applyEnhancements(ClassNode node) {
        boolean modified = false;
        for (MethodNode method : node.methods) {
            if (method.visibleAnnotations != null) {
                for (AnnotationNode annotation : method.visibleAnnotations) {
                    if (annotation.desc.equals(INJECT_ANN)) {
                        List<String> targetMethods = MixinReplacementTransformer.findAnnotationValue(annotation.values, "method", Function.identity());
                        String injectionPoint = MixinReplacementTransformer.<List<AnnotationNode>, String>findAnnotationValue(annotation.values, "at", val -> {
                            AnnotationNode atNode = val.get(0);
                            return findAnnotationValue(atNode.values, "value", Function.identity());
                        });

                        if (targetMethods.contains("<init>") && injectionPoint.equals("INVOKE")) {
                            LOGGER.info("Enhancing injection for mixin {}.{}", node.name, method.name);
                            annotation.desc = "Ldev/su5ed/connector/mod/plugin/injector/EnhancedInject;";
                            modified = true;
                        }
                    }
                }
            }
        }
        return modified;
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

    private boolean applyReplacements(ClassNode node, List<Replacement> replacements) {
        if (!replacements.isEmpty()) {
            List<MethodNode> toRemove = new ArrayList<>();
            for (Replacement replacement : replacements) {
                String mixinMethod = replacement.mixinMethod();
                int descIndex = replacement.mixinMethod().indexOf('(');
                String methodName = descIndex == -1 ? mixinMethod : mixinMethod.substring(0, descIndex);
                String methodDesc = descIndex == -1 ? null : mixinMethod.substring(descIndex);

                for (int i = 0; i < node.methods.size(); i++) {
                    MethodNode method = node.methods.get(i);
                    if (method.name.equals(methodName) && (methodDesc == null || method.desc.equals(methodDesc))) {
                        Pair<Replacement.Action, MethodNode> pair = replacement.apply(node, method);
                        Replacement.Action action = pair.getFirst();

                        if (action == Replacement.Action.REMOVE) {
                            toRemove.add(method);
                        } else if (action == Replacement.Action.REPLACE) {
                            node.methods.set(i, pair.getSecond());
                        }
                    }
                }
            }
            node.methods.removeAll(toRemove);
            return true;
        }
        return false;
    }

    interface Replacement {
        String mixinClass();

        String mixinMethod();

        Pair<Action, MethodNode> apply(ClassNode classNode, MethodNode methodNode);

        static Replacement replaceMixinMethod(String mixinClass, String mixinMethod, String replacementClass) {
            return replaceMixinMethod(mixinClass, mixinMethod, replacementClass, mixinMethod);
        }

        static Replacement replaceMixinMethod(String mixinClass, String mixinMethod, String replacementClass, String replacementMethod) {
            return new ReplaceMixin(mixinClass, mixinMethod, replacementClass, replacementMethod);
        }

        static Replacement removeMixinMethod(String mixinClass, String mixinMethod) {
            return new RemoveMixin(mixinClass, mixinMethod);
        }

        static Replacement redirectMixinTarget(String mixinClass, String mixinMethod, List<String> replacementMethods) {
            return new RedirectMixin(mixinClass, mixinMethod, replacementMethods);
        }

        enum Action {
            KEEP,
            REPLACE,
            REMOVE;

            static Pair<Action, MethodNode> keep() {
                return Pair.of(KEEP, null);
            }

            static Pair<Action, MethodNode> replace(MethodNode replacement) {
                return Pair.of(REPLACE, replacement);
            }

            static Pair<Action, MethodNode> remove() {
                return Pair.of(REMOVE, null);
            }
        }
    }

    record ReplaceMixin(String mixinClass, String mixinMethod, String replacementClass, String replacementMethod) implements Replacement {
        private static final Map<String, ClassNode> CLASS_CACHE = new ConcurrentHashMap<>();

        @Override
        public Pair<Action, MethodNode> apply(ClassNode classNode, MethodNode methodNode) {
            LOGGER.info("Replacing mixin {}.{}", classNode.name, methodNode.name);
            ClassNode replNode = CLASS_CACHE.computeIfAbsent(this.replacementClass, rethrowFunction(c -> {
                ClassReader replReader = new ClassReader(this.replacementClass);
                ClassNode rNode = new ClassNode();
                replReader.accept(rNode, 0);
                return rNode;
            }));

            MethodNode replMethod = replNode.methods.stream().filter(m -> m.name.equals(this.replacementMethod)).findFirst().orElseThrow();
            return Action.replace(replMethod);
        }
    }

    record RemoveMixin(String mixinClass, String mixinMethod) implements Replacement {
        @Override
        public Pair<Action, MethodNode> apply(ClassNode classNode, MethodNode methodNode) {
            LOGGER.info("Removing mixin {}.{}", classNode.name, methodNode.name);
            return Action.remove();
        }
    }

    record RedirectMixin(String mixinClass, String mixinMethod, List<String> replacementMethods) implements Replacement {
        @Override
        public Pair<Action, MethodNode> apply(ClassNode node, MethodNode method) {
            if (method.visibleAnnotations != null) {
                for (AnnotationNode annotation : method.visibleAnnotations) {
                    if (annotation.desc.equals(REDIRECT_ANN)) {
                        List<String> targetMethods = MixinReplacementTransformer.findAnnotationValue(annotation.values, "method", Function.identity());

                        if (targetMethods.size() == 1) {
                            LOGGER.info("Redirecting mixin {}.{} to {}", node.name, method.name, this.replacementMethods);
                            targetMethods.clear();
                            targetMethods.addAll(this.replacementMethods);
                        }
                    }
                }
            }
            return Action.keep();
        }
    }
}
